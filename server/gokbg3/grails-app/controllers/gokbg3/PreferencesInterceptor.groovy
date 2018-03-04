package gokbg3


class PreferencesInterceptor {

  public PreferencesInterceptor() {
      matchAll()
      // matchAll().excludes(controller: 'auth')
  }

  boolean before() {

	log.debug("PreferencesInterceptor::before(${params}) ${request.getRequestURL()}")

    if ( session.sessionPreferences == null ) {
      log.debug("setting session preferences....${grailsApplication.config.appDefaultPrefs ? 'present' : 'Not present'}");
      session.sessionPreferences = grailsApplication.config.appDefaultPrefs
    }
    else {
    }

    true 
  }

  boolean after() {
    true 
  }

  void afterView() {
    // no-op
  }
}
