package org.gokb.cred

import de.wekb.annotations.RefdataAnnotation
import de.wekb.helper.RCConstants

class CuratoryGroup extends KBComponent {

  static belongsTo = User

  User owner

  @RefdataAnnotation(cat = RCConstants.CURATORY_GROUP_TYPE)
  RefdataValue type

  static hasMany = [
    users: User,
  ]

  static mapping = {
    includes KBComponent.mapping
    type column: 'cg_type_rv_fk'
  }

  static mappedBy = [users: "curatoryGroups", ]

  static manyByCombo = [
    licenses: License,
    packages: Package,
    platforms: Platform,
    orgs: Org,
    offices: Office,
    sources: Source
  ]

  static mappedByCombo = [
    licenses: 'curatoryGroups',
    packages: 'curatoryGroups',
    platforms: 'curatoryGroups',
    orgs: 'curatoryGroups',
    offices: 'curatoryGroups',
    sources: 'curatoryGroups'
  ]

  static constraints = {
    owner (nullable:true, blank:false)
    name (validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup(RCConstants.KBCOMPONENT_STATUS, 'Deleted')
          def dupes = CuratoryGroup.findAllByNameIlikeAndStatusNotEqual(val, status_deleted);

          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        } else {
          return ['notNull']
        }
      }
    })
    type (nullable:true, blank:false)
  }

  public String getRestPath() {
    return "/curatoryGroups";
  }

  @Override
  public String getNiceName() {
    return "Curatory Group";
  }

  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookupOrCreate(RCConstants.KBCOMPONENT_STATUS, KBComponent.STATUS_DELETED)
    def ql = null;
    ql = CuratoryGroup.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted ,params)

    ql.each { t ->
      if( !params.filter1 || t.status?.value == params.filter1 ){
        result.add([id:"${t.class.name}:${t.id}", text:"${t.name}", status:"${t.status?.value}"])
      }
    }

    result
  }

  def beforeInsert() {
    def user = springSecurityService?.currentUser
    this.owner = user

    this.generateShortcode()
    this.generateNormname()
    this.generateComponentHash()
    this.generateUuid()
    this.ensureDefaults()
  }
}

