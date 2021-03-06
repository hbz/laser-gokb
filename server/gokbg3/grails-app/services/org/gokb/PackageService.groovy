package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils
import de.wekb.helper.RCConstants
import grails.gorm.transactions.Transactional

import grails.io.IOUtils
import groovy.util.logging.Slf4j
import groovyx.net.http.RESTClient
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.*
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.Session
import org.hibernate.type.StandardBasicTypes
import org.springframework.util.FileCopyUtils

import javax.servlet.ServletOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static groovyx.net.http.Method.GET
import static grails.async.Promises.*

@Slf4j
class PackageService {

  /*
  public static String missingTIPs = '''
    select distinct title, platform
    from TitleInstancePackagePlatform as tipp,
         Combo as title_combo,
         TitleInstance as title,
         Combo as platform_combo,
         Platform as plaform
     where title_combo.fromComponent=tipp
       and title_combo.toComponent=title
       and title_combo.type.value='Title'
       and platform_combo.fromComponent=tipp
       and platform_combo.toComponent=platform
       and platform_combo.type.value='Platform'
       and not exists (
             select tip
             from TitleInstancePlatform as tip,
                  Combo as tip_title_combo,
                  Combo as tip_platform_combo
             where tip_title_combo.fromComponent=tip
               and tip_title_combo.toComponent=title
               and tip_platform_combo.fromComponent=tip
               and tip_platform_combo.toComponent=platform
           )
'''
  */

  def sessionFactory
  def genericOIDService
  def restMappingService
  ComponentLookupService componentLookupService

  public static final enum ExportType {
    KBART, TSV
  }

  /**
   * @return The scope value to be used by "Master Packages"
   */
  private RefdataValue getMasterScope() {
    // The Scope.
    RefdataCategory.lookupOrCreate(RCConstants.PACKAGE_SCOPE, "GOKb Master")
  }

  /**
   * Lookup or create a package based on the supplied package name.
   * Incremental will edit an existing package. If it's false the package and it's
   * TIPPs will have their status set to retired as well and a new package returned.
   */
  def findCorrectPackage(Map<String, Boolean> retired_packages, String package_name, boolean incremental) {

    log.debug("Trying to find a package for ${!incremental ? 'none-incremental' : 'incremental'} update using ${package_name}.")

    // Package.
    Package pkg = componentLookupService.lookupComponent(package_name)

    // If we don't have a package then we need to create one and the incremental flag
    // becomes irrelevant.
    if (!pkg) {

      log.error("No package found for package string supplied via refine. This should not happen as all packages should be looked up.")

    }
    else {

      // If this is a new package then we should retire the current one.
      if (!incremental) {

        if (!retired_packages.get(package_name)) {

          // Retire each TIPP
          pkg.getTipps().each { def tipp ->

            // Retire
            tipp.retire()
            log.debug("TIPP ${tipp.id} retired.")
          }

          // Then retire the package.
          pkg.retire()

          // Create a new package with the IDs
          Set<Identifier> pkIds = pkg.ids.findAll { Identifier the_id ->
            the_id?.getNamespace()?.getValue()?.equalsIgnoreCase('gokb-pkgid')
          }

          // Save the old one.
          if (pkg.save(failOnError: true)) {
            log.debug("Retired and saved package ${pkg.id}.")
          }

          // Get the original name of the package so we can preserve it when recreating.
          String original_name = pkg.name

          // New package.
          pkg = new Package(
              name: original_name
          )

          // Add all the ids.
          pkg.ids.addAll(pkIds)

          // Add to the map.
          retired_packages.put(package_name, true)
        }
      }
    }

    // Save the Package.
    pkg.save(failOnError: true)

    pkg
  }

  /**
   * Method to update all Master list type packages.
   */
  def synchronized updateAllMasters(delta = true) {

    // Create the criteria.
    getAllProviders().each { Org pr ->
      Org.withNewSession({ long prov_id, Session sess ->
        updateMasterFor(pr.id, delta)
      }.curry(pr.id))
    }
    return new Date();
  }

  private def cleanUpGorm() {
    log.debug("Cleaning up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }

  /**
   * Method to create or update a Package containing a list of all titles
   * provided by the supplied Org.
   */
  def updateMasterFor(long provider_id, delta = true) {

    // Read in a provider.
    Org provider = Org.get(provider_id)

    if (provider) {
      log.debug("Update or create master list for ${provider.name}")

      // Get the current master Package for this provider.
      ComboCriteria c = ComboCriteria.createFor(Package.createCriteria())
      Package master = c.get {
        and {
          c.add(
              "scope",
              "eq",
              getMasterScope())
          c.add(
              "provider",
              "eq",
              provider)
        }
      }

      // Update or create?
      if (master) {

        // Update...
        log.debug("Found package ${master.id} for ${provider.name}")

        delta = delta ? master.lastUpdated : false
      }
      else {

        // Set delta to false...
        delta = false

        master = new Package()

        // Need to pass the system_save parameter to flag as systemComponent.
        master.save(failOnError: true)

        // Create new...
        log.debug("Created Master Package ${master.id} for ${provider.name}.")
      }

      master.setName("${provider.name}: Master List")
      master.setScope(getMasterScope())
      master.setProvider(provider)
      master.setSystemComponent(true)

      // Save.
      master.save(failOnError: true)
      provider.save(failOnError: true, flush: true)

      log.debug("Saved Master package ${master.id}")

      // Now query for all packages for the modified since the delta.
      c = ComboCriteria.createFor(Package.createCriteria())
      Set<Package> pkgs = c.list {
        c.and {
          c.add(
              "id",
              "ne",
              master.id)

          c.add(
              "provider.id",
              "eq",
              provider_id)

          if (delta) {
            c.add(
                "lastUpdated",
                "gt",
                delta)
          }
        }
      } as Set

      log.debug("${pkgs.size() ?: 'No'} packages have been updated since the last time this master was updated.")


      for (Package pkg in pkgs) {

        // We should now have a definitive list of tipps that have been changed since the last update.

        // Go through the tipps in chunks.
        //def tipps = pkg.tipps.collect { it.id }

        def tipps = TitleInstancePackagePlatform.executeQuery('select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp', [pkg]);

        log.debug("Query returns ${tipps.size()} tipps");

        TitleInstancePackagePlatform.withNewSession {

          int counter = 1

          for (def t in tipps) {

            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(t)

            // Do we need to update this tipp.
            if (!delta || (delta && tipp.lastUpdated > delta)) {
              TitleInstancePackagePlatform mt = setOrUpdateMasterTippFor(tipp.id, master.id)
            }
            else {
              log.debug("TIPP ${tipp.id} has not been updated since last run. Skipping.")
            }
            log.debug("TIPP ${counter} of ${tipps.size()} examined.")
            counter++
            tipp.discard();
          }
        }
      }
      log.debug("Finished updating master package ${master.id}")
    }
  }

  /**
   * @param tipp the tipp to base the master tipp on.
   * @param master the master package
   * @return the master tipp
   */
  public TitleInstancePackagePlatform setOrUpdateMasterTippFor(long tipp_id, long master_id) {

    Package master = Package.get(master_id)
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

    // Check the tipp isn't already a master.
    Package pkg = tipp.pkg
    if (pkg.status == getMasterScope()) {
      // Throw an exception.
      log.debug("getMasterTipp called for TIPP {tipp.id} that is already a master. Returning supplied tipp.")
      return tipp
    }

    // Master TIPP
    TitleInstancePackagePlatform master_tipp = tipp.masterTipp

    if (!master_tipp) {

      log.debug("No master TIPP associated with this TIPP directly. We should query for one.")

      // Now let's try and read an existing tipp from the master package.
      def mtp = master.getTipps().find {
        (it.title == tipp.getTitle()) &&
            (it.hostPlatform == tipp.getHostPlatform())
      }
      master_tipp = (mtp ? KBComponent.deproxy(mtp) : null)
    }

    if (!master_tipp) {
      // Create a new master tipp.
      master_tipp = tipp.clone().save(failOnError: true)
      log.debug("Added master tipp ${master_tipp.id} to tipp ${tipp.id}")
    }
    else {

      log.debug("Found master tipp ${master_tipp.id} to tipp ${tipp.id}")
      master_tipp = tipp.sync(master_tipp)
    }

    // Ensure certain values are correct.
    master_tipp.with {
      setName(null)
      setPkg(master)
      setSystemComponent(true)
    }


    // Save the master tipp.
    master_tipp.save(failOnError: true)
    master.save(failOnError: true)
    tipp.save(failOnError: true, flush: true)

    // Set as master for faster lookup.
    tipp.setMasterTipp(master_tipp)
    tipp.save(failOnError: true, flush: true)

    // Return the TIPP.
    master_tipp
  }

  /**
   * Get the Set of Orgs currently acting as a provider.
   */
  public Set<Org> getAllProviders() {

    log.debug("Looking for all providers.")

    // The results set.
    LinkedHashSet results = []

    // Create the criteria.
    ComboCriteria c = ComboCriteria.createFor(Package.createCriteria())

    // Query for a list of packages and return the providers.
    def providers = c.list {
      and {
        c.add(
            "status",
            "eq",
            RefdataCategory.lookupOrCreate(RCConstants.KBCOMPONENT_STATUS, KBComponent.STATUS_CURRENT))
      }
    }.each {

      // Add any provider that is set.
      if (it?.provider) {
        results << (it.provider)
      }
    }

    log.debug("Found ${results.size()} providers.")
    results
  }

  @Transactional
  public def generatePackageTypes(Job j = null, def pkg_id = null) {
    log.debug("Generating missing package content types.")
    def result = [book: 0, db: 0, journal: 0, mixed: 0, errors: 0]
    def pkg_list = []

    if (!pkg_id) {
      pkg_list = Package.executeQuery("select id from Package where contentType is null")
    }
    else {
      pkg_list << pkg_id
    }

    def msg_list = []
    def rdv_journal = RefdataCategory.lookup(RCConstants.TITLEINSTANCE_MEDIUM, "Journal")
    def rdv_book = RefdataCategory.lookup(RCConstants.TITLEINSTANCE_MEDIUM, "Book")
    def rdv_db = RefdataCategory.lookup(RCConstants.TITLEINSTANCE_MEDIUM, "Database")
    def ctr = 0

    for (pkg in pkg_list) {

      Package.withNewTransaction {

        def pkg_obj = Package.get(pkg)
        def has_db = pkg_obj.tipps.title.find { it.medium == rdv_db }
        def has_journal = pkg_obj.tipps.title.find { it.medium == rdv_journal }
        def has_book = pkg_obj.tipps.title.find { it.medium == rdv_book }

        if (has_db && !has_journal && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup(RCConstants.PACKAGE_CONTENT_TYPE, 'Database')
          result.db++
        }
        else if (has_journal && !has_db && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup(RCConstants.PACKAGE_CONTENT_TYPE, 'Journal')
          result.journal++
        }
        else if (has_book && !has_db && !has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup(RCConstants.PACKAGE_CONTENT_TYPE, 'Book')
          result.book++
        }
        else if (has_book && has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup(RCConstants.PACKAGE_CONTENT_TYPE, 'Mixed')
          result.mixed++
        }
        else if (!has_book && !has_journal && !has_db) {
          log.debug("No content to categorize for ${pkg_obj.name}")
        }
        else {
          def msg = "Found illegal package composition for ${pkg_obj.name} (journals: ${has_journal}, db: ${has_db}, book: ${has_book})!"
          log.warn(msg)
          result.errors++
          msg_list.add(msg)
        }
        ctr++
        if (j) {
          j.setProgress(ctr, pkg_list.size())
        }
      }
    }

    if (j) {
      j.message("Finished creating ${ctr} new types (${result.errors} errors).".toString())

      msg_list.each {
        j.message(it.toString())
      }

      j.endTime = new Date()
    }
  }

  @Transactional
  def compareLists(listOne, listTwo, def full = true, Date date = null, Job j = null) {
    def result = [:]
    def status_current = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Current')
    def status_retired = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Retired')
    def status_expected = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Expected')
    def status_deleted = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Deleted')
    def tipp_status = [status_current]
    Date checkDate = date ?: new Date()
    def tipp_params = [:]
    def totals = [one: [tipps: 0, titles: 0], two: [tipps: 0, titles: 0]]
    def titlesOne = [:]
    def titlesTwo = [:]
    def currentPkgNum = 0
    boolean cancelled = false

    if (date) {
      if (date.before(new Date())) {
        tipp_status << status_retired
      }
    }

    if (full) {
      result = ['new': [], 'both':[], 'missing':[]]
    }
    else {
      result = ['new': 0, 'both': 0, 'missing': 0]
    }

    log.debug("Building titles map 1 ..")

    Package.withNewSession {
      for (p1 in listOne) {
        def pkg = Package.get(genericOIDService.oidToId(p1))
        currentPkgNum++

        if (pkg && !cancelled) {
          int total = TitleInstancePackagePlatform.executeQuery("select count(*) from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg])[0]
          int currentOffset = 0

          while (currentOffset < total) {
            def tipps = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg])

            tipps.each { tipp ->
              def inRange = true

              if (tipp.accessEndDate && tipp.accessEndDate.before(checkDate)) {
                inRange = false
              }
              else if (tipp.accessStartDate && tipp.accessStartDate.after(checkDate)) {
                inRange = false
              }
              else if (tipp.status == status_retired) {
                if (date && tipp.lastUpdated?.before(checkDate)) {
                  inRange = false
                }
              }

              if (inRange) {
                if (!titlesOne[tipp.title.id]){
                  titlesOne[tipp.title.id] = [id: tipp.title.id, name: tipp.title.name, tipps: []]
                  totals.one.titles++
                }

                totals.one.tipps++
                titlesOne[tipp.title.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)
              }
              currentOffset++
            }
            cleanUpGorm()
          }

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }

          if (j) {
            j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
          }
        }
        else if (!pkg) {
          log.debug("Unable to resolve Package with id ${p1}")
        }
      }

      log.debug("Added ${totals.one.titles} titles with ${totals.one.tipps} TIPPs!")

      log.debug("Building titles map 2 ..")

      for (p2 in listTwo) {
        def pkg = Package.get(genericOIDService.oidToId(p2))
        currentPkgNum++

        if (pkg && !cancelled) {
          int total = TitleInstancePackagePlatform.executeQuery("select count(*) from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg])[0]
          int currentOffset = 0

          while (currentOffset < total) {
            def tipps = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg], [max: 50, offset: currentOffset])

            tipps.each { tipp ->
              def inRange = true

              if (tipp.accessEndDate && tipp.accessEndDate.before(checkDate)) {
                inRange = false
              }
              else if (tipp.accessStartDate && tipp.accessStartDate.after(checkDate)) {
                inRange = false
              }
              else if (tipp.status == status_retired) {
                if (date && tipp.lastUpdated.before(checkDate)) {
                  inRange = false
                }
              }

              if (inRange) {
                if (!titlesTwo[tipp.title.id]){
                  titlesTwo[tipp.title.id] = [id: tipp.title.id, name: tipp.title.name, tipps: []]
                  totals.two.titles++
                }

                totals.two.tipps++
                titlesTwo[tipp.title.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)

                if (!titlesOne[tipp.title.id]) {
                  if (full) {
                    result['new'] << restMappingService.mapObjectToJson(tipp, tipp_params)
                  }
                  else {
                    result['new']++
                  }
                }
              }
              currentOffset++
            }
          }

          cleanUpGorm()

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }

          if (j) {
            j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
          }
        }
      }
    }

    log.debug("Finished collecting TIPPs. Starting comparison")

    if (!cancelled) {
      titlesOne.each { id, val ->
        if (!titlesTwo[id]) {
          if (full) {
            result['missing'] << val
          }
          else {
            result['missing']++
          }
        }
        else {
          if (full) {
            result['both'] << ['id': val.id, 'name': val.name, 'old': val.tipps, 'new': titlesTwo[id].tipps]
          }
          else {
            result['both']++
          }
        }
      }

      titlesTwo.each { id, val ->
        if (!titlesOne[id]) {
          if (full) {
            result['new'] << val
          }
          else {
            result['new']++
          }
        }
      }
    }

    if (j) {
      j.endTime = new Date()
    }

    log.debug("Added ${totals.two.titles} titles with ${totals.two.tipps} TIPPs!")
    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  def restLookup(packageHeaderDTO, def user = null) {
    log.info("Upsert org with header ${packageHeaderDTO}");
    def result = [to_create: true];
    def status_deleted = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Deleted')
    def normname = Package.generateNormname(packageHeaderDTO.name)

    log.debug("Checking by normname ${normname} ..")
    def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [normname, status_deleted])
    def ids_list = packageHeaderDTO.identifiers ?: packageHeaderDTO.ids
    def matches = [:]
    def created = false
    boolean changed = false;

    if (name_candidates.size() > 0) {
      name_candidates.each { nc ->
        if (!matches["${nc.id}"])
          matches["${nc.id}"] = []

        matches["${nc.id}"] << [field: 'name', value: packageHeaderDTO.name, message: "Another package with this name already exists!"]
      }
    }

    if (packageHeaderDTO.ids?.size() > 0) {
      ids_list.each { rid ->
        Identifier the_id = null

        if (rid instanceof Integer) {
          the_id = Identifier.get(rid)
        }
        else {
          def ns_field = rid.type ?: rid.namespace
          def ns = null

          if (ns_field) {
            if (ns_field instanceof Integer) {
              ns = IdentifierNamespace.get(ns_field)
            }
            else {
              ns = IdentifierNamespace.findByValueIlike(ns_field)
            }

            if (ns) {
              def match = Package.lookupByIO(ns.value, rid.value)

              if (match) {
                if (!matches["${nc.id}"])
                  matches["${nc.id}"] = []

                matches["${nc.id}"] << [field: 'ids', value: rid.value, message: "An existing package was matched by a supplied identifier!"]
              }
            }
          }
        }
      }
    }

    def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
    def variant_matches = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

    variant_matches.each { vm ->
      if (!matches["${vm.id}"])
        matches["${vm.id}"] = []

      matches["${vm.id}"] << ['field': 'name', value: packageHeaderDTO.name, message: "Provided name matched a variant of an existing package!"]
    }

    if (packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      packageHeaderDTO.variantNames.each {
        def variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if (variant) {
          def name_matches = Package.findAllByName(variant)

          name_matches.each { nm ->
            if (!matches["${nm.id}"])
              matches["${nm.id}"] = []

            matches["${nm.id}"] << [field: 'variantNames', value: variant, message: "Provided variant matched the title of an existing package!"]
          }

          def variant_nn = GOKbTextUtils.normaliseString(variant)
          def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_nn, status_deleted]);

          variant_candidates.each { vc ->
            log.debug("Found existing package variant name for variantName ${variant}")
            if (!matches["${vc.id}"])
              matches["${vc.id}"] = []

            matches["${vc.id}"] << ['field': 'variantNames', value: variant, message: "Provided variant matched that of an existing package!"]
          }
        }
      }
    }

    if (matches.size() > 0) {
      result.to_create = false
      result.matches = matches
    }

    result
  }

  /**
   * Definitive rules for taking a package header DTO and inserting or updating an existing package based on package name
   *
   * status:'Current',
   * breakable:'Unknown',
   * consistent:'Unknown',
   * paymentType:'Unknown',
   * nominalPlatform:54678
   * provider:4325
   * source:[
   *   url:'http://www.zeitschriftendatenbank.de'
   *   defaultAccessURL:''
   *   explanationAtSource:''
   *   contextualNotes:''
   *   frequency:''
   *   ruleset:''
   *   defaultSupplyMethod:'Other'
   *   defaultDataFormat:'Other'
   *   responsibleParty:''
   * ]
   * name:'Campus: All Journals'
   * curatoryGroups:[
   *   curatoryGroup:"SuUB Bremen"
   * ]
   * variantNames : [
   *   variantName:"Campus: All Journals"
   * ]
   */

  @Transactional
  public Package upsertDTO(packageHeaderDTO, def user = null) {
    log.info("Upsert package with header ${packageHeaderDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate(RCConstants.KBCOMPONENT_STATUS, 'Deleted')
    def pkg_normname = Package.generateNormname(packageHeaderDTO.name)

    log.debug("Checking by normname ${pkg_normname} ..")
    def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [pkg_normname, status_deleted])
    def full_matches = []
    def created = false
    def result = packageHeaderDTO.uuid ? Package.findByUuid(packageHeaderDTO.uuid) : null;
    boolean changed = false;

    if (!result && name_candidates.size() > 0 && packageHeaderDTO.identifiers?.size() > 0) {
      log.debug("Got ${name_candidates.size()} matches by name. Checking against identifiers!")
      name_candidates.each { mp ->
        if (mp.ids.size() > 0) {
          def id_match = false;

          packageHeaderDTO.identifiers.each { rid ->

            //TODO: MOE
            Identifier the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(rid.type, rid.value);

            if (mp.ids.contains(the_id)) {
              id_match = true
            }
          }

          if (id_match && !full_matches.contains(mp)) {
            full_matches.add(mp)
          }
        }
      }

      if (full_matches.size() == 1) {
        log.debug("Matched package by name + identifier!")
        result = full_matches[0]
      }
      else if (full_matches.size() == 0 && name_candidates.size() == 1) {
        result = name_candidates[0]
        log.debug("Found a single match by name!")
      }
      else {
        log.warn("Found multiple possible matches for package! Aborting..")
        return result
      }
    }
    else if (!result && name_candidates.size() == 1) {
      log.debug("Matched package by name!")
      result = name_candidates[0]
    }
    else if (result && result.name != packageHeaderDTO.name) {
      def current_name = result.name

      changed |= ClassUtils.setStringIfDifferent(result, 'name', packageHeaderDTO.name)

      if (!result.variantNames.find { it.variantName == current_name }) {
        result.ensureVariantName(current_name)
      }
    }

    if (!result) {
      log.debug("Did not find a match via name, trying existing variantNames..")
      def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
      def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

      if (variant_candidates.size() == 1) {
        result = variant_candidates[0]
        log.debug("Package matched via existing variantName.")
      }
    }

    if (!result && packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      packageHeaderDTO.variantNames.each {

        if (it.trim().size() > 0) {
          result = Package.findByName(it)

          if (result) {
            log.debug("Found existing package name for variantName ${it}")
          }
          else {
            def variant_normname = GOKbTextUtils.normaliseString(it)
            def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);
            if (variant_candidates.size() == 1) {
              log.debug("Found existing package variant name for variantName ${it}")
              result = variant_candidates[0]
            }
          }
        }
      }
    }

    if (!result) {
      log.debug("No existing package matched. Creating new package..")
      result = new Package(name: packageHeaderDTO.name, normname: pkg_normname)
      created = true
      if (packageHeaderDTO.uuid && packageHeaderDTO.uuid.trim().size() > 0) {
        result.uuid = packageHeaderDTO.uuid
      }
      result.save(flush: true, failOnError: true)
    }
    else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
      def cur = user.curatoryGroups?.id.intersect(result.curatoryGroups?.id)
      if (!cur) {
        log.debug("No curator!")
        return result
      }
    }

    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.status, result, 'status')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.scope, result, 'scope')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.breakable, result, 'breakable')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.consistent, result, 'consistent')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.paymentType, result, 'paymentType')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.file, result, 'file')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.openAccess, result, 'openAccess')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.contentType, result, 'contentType')

    // Platform
    Platform newPlatform = null
    if (packageHeaderDTO.nominalPlatform) {
      def platformDTO = [:];

      if (packageHeaderDTO.nominalPlatform instanceof String && packageHeaderDTO.nominalPlatform.trim()) {
        platformDTO['name'] = packageHeaderDTO.nominalPlatform
      }
      else if (packageHeaderDTO.nominalPlatform instanceof Integer) {
        platformDTO['id'] = (Long) packageHeaderDTO.nominalPlatform
      }
      else if (packageHeaderDTO.nominalPlatform.name && packageHeaderDTO.nominalPlatform.name.trim().size() > 0) {
        platformDTO = packageHeaderDTO.nominalPlatform
      }

      if (platformDTO) {
        def np = null
        if (platformDTO.uuid) {
          np = Platform.findByUuid(platformDTO.uuid)
        }
        else if (platformDTO.id) {
          np = Platform.get(platformDTO.id)
        }
        else if (platformDTO.name) {
          np = Platform.findByName(platformDTO.name)
        }
        if (!np && platformDTO.name) {
          np = Platform.upsertDTO(platformDTO)
          newPlatform = np
        }
        if (np) {
          if (result.nominalPlatform == null) {
            result.nominalPlatform = np;
            changed = true
          }
          else {
            log.debug("Platform already set")
          }
        }
        else {
          log.warn("Unable to locate nominal platform ${packageHeaderDTO.nominalPlatform}");
        }
      }
      else {
        log.warn("Could not extract platform information from JSON!")
      }
    }

    // Provider

    if (packageHeaderDTO.nominalProvider) {

      def providerDTO = [:]

      if (packageHeaderDTO.nominalProvider instanceof String && packageHeaderDTO.nominalProvider.trim()) {
        providerDTO['name'] = packageHeaderDTO.nominalProvider
      }
      else if (packageHeaderDTO.nominalProvider.name && packageHeaderDTO.nominalProvider.name.trim()) {
        providerDTO = packageHeaderDTO.nominalProvider
      }
      log.debug("Trying to set package provider.. ${providerDTO}")
      def prov = null
      if (providerDTO?.uuid) {
        prov = Org.findByUuid(providerDTO.uuid)
      }
      if (providerDTO && !prov) {
        def norm_prov_name = KBComponent.generateNormname(providerDTO.name)
        prov = Org.findByNormname(norm_prov_name)
        if (!prov) {
          log.debug("None found by Normname ${norm_prov_name}, trying variants")
          def variant_normname = GOKbTextUtils.normaliseString(providerDTO.name)
          def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status = ?", [variant_normname, status_deleted]);
          if (candidate_orgs.size() == 1) {
            prov = candidate_orgs[0]
          }
          else if (candidate_orgs.size() == 0) {
            log.debug("No org match for provider ${packageHeaderDTO.nominalProvider}. Creating new org..")
            prov = new Org(name: providerDTO.name, normname: norm_prov_name, uuid: providerDTO.uuid ?: null).save(flush: true, failOnError: true);
          }
          else {
            log.warn("Multiple org matches for provider ${packageHeaderDTO.nominalProvider}. Skipping..");
          }
        }
      }
      if (prov) {
        if (result.provider == null) {
          result.provider = prov;
          log.debug("Provider ${prov.name} set.")
          changed = true
        }
        else {
          log.debug("No provider change")
        }
      }
    }
    else {
      log.debug("No provider found!")
    }

    // Source
    // variantNames are handled in ComponentUpdateService
    // packageHeaderDTO.variantNames?.each {
    //   if ( it.trim().size() > 0 ) {
    //     result.ensureVariantName(it)
    //     changed=true
    //   }
    // }

    // CuratoryGroups
    packageHeaderDTO.curatoryGroups?.each {
      def cg = null
      def cgname = null

      if (it instanceof Integer) {
        cg = CuratoryGroup.get(it)
      }
      else if (it instanceof String) {
        String normname = CuratoryGroup.generateNormname(it)
        cgname = it
        cg = CuratoryGroup.findByNormname(normname)
      }
      else if (it.id) {
        cg = CuratoryGroup.get(it.id)
      }
      else if (it.name) {
        String normname = CuratoryGroup.generateNormname(it.name)
        cgname = it.name
        cg = CuratoryGroup.findByNormname(normname)
      }
      if (cg) {
        if (result.curatoryGroups.find { it.name == cg.name }) {
        }
        else {
          result.curatoryGroups.add(cg)
          changed = true;
        }
        //If new Platform set curatoryGroups
        if(newPlatform) {
          newPlatform.curatoryGroups.add(cg)
        }

      }
      else if (cgname) {
        def new_cg = new CuratoryGroup(name: cgname).save(flush: true, failOnError: true)
        result.curatoryGroups.add(new_cg)
        newPlatform.curatoryGroups.add(new_cg)
        changed = true
      }
    }

    if (packageHeaderDTO.source) {
      def src = null
      if (packageHeaderDTO.source instanceof Integer) {
        src = Source.get(packageHeaderDTO.source)
      }
      else if (packageHeaderDTO.source instanceof Map) {
        def sourceMap = packageHeaderDTO.source
        if (sourceMap.id) {
          src = Source.get(sourceMap.id)
        }
        else {
          def namespace = null
          if (sourceMap.targetNamespace instanceof Integer) {
            namespace = IdentifierNamespace.get(sourceMap.targetNamespace)
          }
          if (!result.source || result.source.name != result.name) {
            def source_config = [
                name           : result.name,
                url            : sourceMap.url,
                frequency      : sourceMap.frequency,
                ezbMatch       : (sourceMap.ezbMatch ?: false),
                zdbMatch       : (sourceMap.zdbMatch ?: false),
                automaticUpdate: (sourceMap.automaticUpdate ?: false),
                targetNamespace: namespace
            ]
            src = new Source(source_config).save(flush: true)
            result.curatoryGroups.each { cg ->
              src.curatoryGroups.add(cg)
            }
          }
          else {
            src = result.source
            changed |= ClassUtils.setStringIfDifferent(src, 'frequency', sourceMap.frequency)
            changed |= ClassUtils.setStringIfDifferent(src, 'url', sourceMap.url)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'ezbMatch', sourceMap.ezbMatch)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'zdbMatch', sourceMap.zdbMatch)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'automaticUpdate', sourceMap.automaticUpdate)
            if (namespace && namespace != src.targetNamespace) {
              src.targetNamespace = namespace
              changed = true
            }
            src.save(flush: true)
          }
        }
      }
      if (src && result.source != src) {
        result.source = src
        changed = true
      }
    }

    if (packageHeaderDTO.ddcs) {
      packageHeaderDTO.ddcs.each{ String ddc ->
        RefdataValue refdataValue = RefdataCategory.lookup(RCConstants.DDC, ddc)

        if(refdataValue && !(refdataValue in result.ddcs)){
          result.addToDdcs(refdataValue)
        }
      }
    }

    result.save(flush: true)
    result
  }


  /**
   * collects the data of the given package into a KBART formatted TSV file for later download
   */
  /*void createKbartExport(Package pkg, def response) {
    if (pkg) {
      try {
        ServletOutputStream out = response.outputStream
        out.withWriter { writer ->

          def sanitize = { it ? "${it}".trim() : "" }

          // As per spec header at top of file / section
          // II: Need to add in preceding_publication_title_id
          writer.write('publication_title\t' +
              'print_identifier\t' +
              'online_identifier\t' +
              'date_first_issue_online\t' +
              'num_first_vol_online\t' +
              'num_first_issue_online\t' +
              'date_last_issue_online\t' +
              'num_last_vol_online\t' +
              'num_last_issue_online\t' +
              'title_url\t' +
              'first_author\t' +
              'title_id\t' +
              'embargo_info\t' +
              'coverage_depth\t' +
              'coverage_notes\t' +
              'publisher_name\t' +
              'preceding_publication_title_id\t' +
              'date_monograph_published_print\t' +
              'date_monograph_published_online\t' +
              'monograph_volume\t' +
              'monograph_edition\t' +
              'first_editor\t' +
              'parent_publication_title_id\t' +
              'publication_type\t' +
              'access_type\t' +
              'zdb_id\t' +
              'gokb_tipp_uid\t' +
              'gokb_title_uid\n');

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup(RCConstants.COMBO_TYPE, 'Package.Tipps')
          def status_current = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Current')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status = :sc and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sc', status_current)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {
            def tipp_id = tipps.get(0);

            TitleInstancePackagePlatform.withNewSession {
              def tipp = TitleInstancePackagePlatform.get(tipp_id)
              def pub_type = tipp.title?.niceName == 'Book' ? 'Monograph' : 'Serial'
              def print_id = ""
              def online_id = ""

              if (tipp.title.hasProperty('dateFirstInPrint')) {
                def pid = tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')
                def oid = tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')

                if (pid) {
                  print_id = pid
                }
                if (oid) {
                  online_id = oid
                }
              }
              else {
                def pid = tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')
                def oid = tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')

                if (pid) {
                  print_id = pid
                }
                if (oid) {
                  online_id = oid
                }
              }

              if (tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { cst ->
                  writer.write(
                      sanitize(tipp.name ?: tipp.title.name) + '\t' +
                          print_id + '\t' +
                          online_id + '\t' +
                          sanitize(cst.startDate) + '\t' +
                          sanitize(cst.startVolume) + '\t' +
                          sanitize(cst.startIssue) + '\t' +
                          sanitize(cst.endDate) + '\t' +
                          sanitize(cst.endVolume) + '\t' +
                          sanitize(cst.endIssue) + '\t' +
                          sanitize(tipp.url) + '\t' +
                          (tipp.title.hasProperty('firstAuthor') ? sanitize(tipp.title.firstAuthor) : '') + '\t' +
                          sanitize(tipp.title.getId()) + '\t' +
                          sanitize(cst.embargo) + '\t' +
                          sanitize(cst.coverageDepth).toLowerCase() + '\t' +
                          sanitize(cst.coverageNote) + '\t' +
                          sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                          sanitize(tipp.title.getPrecedingTitleId()) + '\t' +
                          (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.dateFirstInPrint) : '') + '\t' +
                          (tipp.title.hasProperty('dateFirstOnline') ? sanitize(tipp.title.dateFirstOnline) : '') + '\t' +
                          (tipp.title.hasProperty('volumeNumber') ? sanitize(tipp.title.volumeNumber) : '') + '\t' +
                          (tipp.title.hasProperty('editionStatement') ? sanitize(tipp.title.editionStatement) : '') + '\t' +
                          (tipp.title.hasProperty('firstEditor') ? sanitize(tipp.title.firstEditor) : '') + '\t' +
                          '\t' +  // parent_publication_title_id
                          sanitize(pub_type) + '\t' +  // publication_type
                          sanitize(tipp.paymentType?.value) + '\t' +  // access_type
                          sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                          sanitize(tipp.uuid) + '\t' +
                          sanitize(tipp.title.uuid) +
                          '\n');
                }
              }
              else {
                writer.write(
                    sanitize(tipp.name ?: tipp.title.name) + '\t' +
                        print_id + '\t' +
                        online_id + '\t' +
                        sanitize(tipp.url) + '\t' +
                        (tipp.title.hasProperty('firstAuthor') ? sanitize(tipp.title.firstAuthor) : '') + '\t' +
                        sanitize(tipp.title.getId()) + '\t' +
                        sanitize(tipp.coverageDepth).toLowerCase() + '\t' +
                        sanitize(tipp.coverageNote) + '\t' +
                        sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                        sanitize(tipp.title.getPrecedingTitleId()) + '\t' +
                        (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.dateFirstInPrint) : '') + '\t' +
                        (tipp.title.hasProperty('dateFirstOnline') ? sanitize(tipp.title.dateFirstOnline) : '') + '\t' +
                        (tipp.title.hasProperty('volumeNumber') ? sanitize(tipp.title.volumeNumber) : '' + '\t') +
                        (tipp.title.hasProperty('editionStatement') ? sanitize(tipp.title.editionStatement) : '') + '\t' +
                        (tipp.title.hasProperty('firstEditor') ? sanitize(tipp.title.firstEditor) : '') + '\t' +
                        '\t' +  // parent_publication_title_id
                        sanitize(pub_type) + '\t' +  // publication_type
                        sanitize(tipp.paymentType?.value) + '\t' +  // access_type
                        sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                        sanitize(tipp.uuid) + '\t' +
                        sanitize(tipp.title.uuid) +
                        '\n');
              }
            }
          }
          tipps.close()

          writer.flush();
          writer.close();
        }
        out.flush()
        out.close()
      }
      catch (Exception e) {
        log.error("Problem with creating KBART export data", e);
      }
    }
  }*/

  /*public void createTsvExport(Package pkg, def response) {
    def export_date = dateFormatService.formatDate(new Date());
    try {
      if (pkg) {
        String lastUpdate = dateFormatService.formatDate(pkg.lastUpdated)
        ServletOutputStream out = response.outputStream
        out.withWriter { writer ->
          def sanitize = { it ? "${it}".trim() : "" }

          // As per spec header at top of file / section
          writer.write("we:kb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID\t' +
              'TIPP URL\t' +
              'Title ID\t' +
              'Title\t' +
              'TIPP Status\t' +
              '[TI] Publisher\t' +
              '[TI] Imprint\t' +
              '[TI] Published From\t' +
              '[TI] Published to\t' +
              '[TI] Medium\t' +
              '[TI] OA Status\t' +
              '[TI] Continuing series\t' +
              '[TI] ISSN\t' +
              '[TI] EISSN\t' +
              '[TI] ZDB-ID\t' +
              'Package\t' +
              'Package ID\t' +
              'Package URL\t' +
              'Platform\t' +
              'Platform URL\t' +
              'Platform ID\t' +
              'Reference\t' +
              'Edit Status\t' +
              'Access Start Date\t' +
              'Access End Date\t' +
              'Coverage Start Date\t' +
              'Coverage Start Volume\t' +
              'Coverage Start Issue\t' +
              'Coverage End Date\t' +
              'Coverage End Volume\t' +
              'Coverage End Issue\t' +
              'Embargo\t' +
              'Coverage depth\t' +
              'Coverage note\t' +
              'Host Platform URL\t' +
              'Format\t' +
              'Payment Type\t' +
              '[TI] DOI\t' +
              '[TI] ISBN\t' +
              '[TI] pISBN' +
              '\n');

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup(RCConstants.COMBO_TYPE, 'Package.Tipps')
          def status_deleted = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Deleted')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status <> :sd and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sd', status_deleted)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {

            def tipp_id = tipps.get(0);
            TitleInstancePackagePlatform.withNewSession {
              TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

              if (tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { tcs ->
                  writer.write(
                      sanitize(tipp.getId()) + '\t' +
                          sanitize(tipp.url) + '\t' +
                          sanitize(tipp.title.getId()) + '\t' +
                          sanitize(tipp.name ?: tipp.title.name) + '\t' +
                          sanitize(tipp.status.value) + '\t' +
                          sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                          sanitize(tipp.title.imprint?.name) + '\t' +
                          sanitize(tipp.title.publishedFrom) + '\t' +
                          sanitize(tipp.title.publishedTo) + '\t' +
                          sanitize(tipp.title.medium?.value) + '\t' +
                          sanitize(tipp.title.OAStatus?.value) + '\t' +
                          sanitize(tipp.title.continuingSeries?.value) + '\t' +
                          sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                          sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.hostPlatform.name) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.hostPlatform.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.accessStartDate) + '\t' +
                          sanitize(tipp.accessEndDate) + '\t' +
                          sanitize(tcs.startDate) + '\t' +
                          sanitize(tcs.startVolume) + '\t' +
                          sanitize(tcs.startIssue) + '\t' +
                          sanitize(tcs.endDate) + '\t' +
                          sanitize(tcs.endVolume) + '\t' +
                          sanitize(tcs.endIssue) + '\t' +
                          sanitize(tcs.embargo) + '\t' +
                          sanitize(tcs.coverageDepth) + '\t' +
                          sanitize(tcs.coverageNote) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title.getIdentifierValue('DOI')) + '\t' +
                          sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')) +
                          '\n');
                }
              }
              else {
                writer.write(
                    sanitize(tipp.getId()) + '\t' +
                        sanitize(tipp.url) + '\t' +
                        sanitize(tipp.title.getId()) + '\t' +
                        sanitize(tipp.name ?: tipp.title.name) + '\t' +
                        sanitize(tipp.status.value) + '\t' +
                        sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                        sanitize(tipp.title.imprint?.name) + '\t' +
                        sanitize(tipp.title.publishedFrom) + '\t' +
                        sanitize(tipp.title.publishedTo) + '\t' +
                        sanitize(tipp.title.medium?.value) + '\t' +
                        sanitize(tipp.title.OAStatus?.value) + '\t' +
                        sanitize(tipp.title.continuingSeries?.value) + '\t' +
                        sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                        sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.hostPlatform?.name) + '\t' +
                        sanitize(tipp.hostPlatform?.primaryUrl) + '\t' +
                        sanitize(tipp.hostPlatform?.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.accessStartDate) + '\t' +
                        sanitize(tipp.accessEndDate) + '\t' +
                        sanitize(tipp.coverageDepth) + '\t' +
                        sanitize(tipp.coverageNote) + '\t' +
                        sanitize(tipp.hostPlatform?.primaryUrl) + '\t' +
                        sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title.getIdentifierValue('DOI')) + '\t' +
                        sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')) +
                        '\n');
              }
              tipp.discard();
            }
          }
          tipps.close()

          writer.flush();
          writer.close();
        }
        out.flush()
        out.close()
      }
    }
    catch (Exception e) {
      log.error("Problem with writing tsv export file", e);
    }
  }*/

  /*void sendFile(Package pkg, ExportType type, def response) {
    String fileName = generateExportFileName(pkg, type)
    response.setContentType('text/tab-separated-values');
    response.setHeader("Content-Disposition", "attachment; filename=\"${fileName.substring(0, fileName.length() - 13)}.tsv\"")
    response.setHeader("Content-Encoding", "UTF-8")
    try {
        if (type == ExportType.KBART)
          createKbartExport(pkg, response)
        else
          createTsvExport(pkg, response)
    }
    catch (Exception e) {
      log.error("Problem with sending export", e);
    }
  }*/

  /*public void sendZip(Collection packs, ExportType type, def response) {
    def pathPrefix = UUID.randomUUID().toString()
    File tempDir = new File(exportFilePath() + "/" + pathPrefix)
    tempDir.mkdir()
    // step one: collect data files in temp directory
    packs.each { pkg ->
      String fileName = generateExportFileName(pkg, type)
      try {
        File src = new File(exportFilePath() + fileName)
        if (!src.isFile()) {
          if (type == ExportType.KBART)
            createKbartExport(pkg, response)
          else
            createTsvExport(pkg, response)
          src = new File(exportFilePath() + fileName)
        }
        File dest = new File("${exportFilePath()}/${pathPrefix}/${fileName.substring(0, fileName.length() - 13)}.tsv")
        FileCopyUtils.copy(src, dest)
      } catch (IOException iox) {
        log.error("Problem while collecting data", iox)
      }
    }

    // step two: zip data
    def zipFileName = exportFilePath() + "gokbExport_${pathPrefix}.zip"
    ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(zipFileName))
    new File("${exportFilePath()}/$pathPrefix").eachFile() { file ->
      //check if file
      if (file.isFile()) {
        zipFile.putNextEntry(new ZipEntry(file.name))
        def buffer = new byte[file.size()]
        file.withInputStream {
          zipFile.write(buffer, 0, it.read(buffer))
        }
        zipFile.closeEntry()
      }
    }
    zipFile.close()

    // step three: copy the zipfile into the response
    File file = new File(zipFileName)
    response.setContentType('application/octet-stream');
    response.setHeader("Content-Disposition", "attachment; filename=\"gokbExport.zip\"")
    response.setHeader("Content-Description", "File Transfer")
    response.setHeader("Content-Transfer-Encoding", "binary")
    response.setContentLength(file.length())

    InputStream input = new FileInputStream(file)
    OutputStream output = response.outputStream
    IOUtils.copy(input, output)
    output.close()
    input.close()
  }*/

  /*private String generateExportFileName(Package pkg, ExportType type) {
    String lastUpdate = dateFormatService.formatTimestamp(pkg.lastUpdated)
    StringBuilder name = new StringBuilder()
    if (type == ExportType.KBART) {
      name.append(toCamelCase(pkg.provider ? pkg.provider.name : "unknown Provider")).append('_')
          .append(toCamelCase(pkg.scope ? pkg.scope.value : '')).append('_')
          .append(toCamelCase(pkg.name))
    }
    else {
      name.append("GoKBPackage-").append(pkg.id)
    }
    name.append('_').append(lastUpdate).append('.tsv')
    return name.toString()
  }*/

  /*private String exportFilePath() {
    String exportPath = grailsApplication.config.gokb.tsvExportTempDirectory ?: "/tmp/gokb/export"
    Files.createDirectories(Paths.get(exportPath))
    exportPath.endsWith('/') ? exportPath : exportPath + '/'
  }*/

  private String toCamelCase(String before) {
    StringBuilder ret = new StringBuilder()
    before.split("\\W").each { word ->
      if (word.length() > 0)
        ret.append(word.substring(0, 1).toUpperCase())
            .append(word.substring(1, word.length()).toLowerCase())
    }
    ret.toString()
  }
}
