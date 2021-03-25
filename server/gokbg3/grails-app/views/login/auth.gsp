<g:set var='securityConfig' value='${applicationContext.springSecurityService.securityConfig}'/>
<html>
<head>
  <meta name="layout" content="public"/>
  <s2ui:title messageCode='springSecurity.login.header'/>
  <asset:stylesheet src='spring-security-ui-auth.css'/>
</head>
<body>
  <div class="container-fluid">
    <div class="row">
      <div class="col-lg-6 col-lg-offset-3">
            <g:form class="well" controller="login" action="authenticate" method="post" name="loginForm" elementId="loginForm" autocomplete="off">
            <h2>Login</h2>
              <g:if test="${params.login_error}">
                <div class="alert alert-danger"><g:message code='springSecurity.login.error.message'/></div>
              </g:if>
               <div class="form-group">
                 <label for="username"><g:message code='springSecurity.login.username.label'/></label>
                 <input type="text" class="form-control" id="username" aria-describedby="usernameHelp" placeholder="Username" name="${securityConfig.apf.usernameParameter}">
               </div>

               <div class="form-group">
                 <label for="password"><g:message code='springSecurity.login.password.label'/></label>
                 <input type="password" class="form-control" id="password" aria-describedby="passwordHelp" placeholder="" name="${securityConfig.apf.passwordParameter}">
               </div>

               <!-- input type="checkbox" class="checkbox" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked"-->
               <!-- label for='remember_me'><g:message code='spring.security.ui.login.rememberme'/></label -->
               <button type="submit">Login</button>
                <small style="margin-left:20px;"><g:link controller="register" action="forgotPassword"><g:message code="spring.security.ui.login.forgotPassword" /></g:link></small>

                <small style="margin-left:20px;"><a data-toggle="modal" data-cache="false"
                                                    data-target="#infoModal">Not yet registered for a <g:message code="gokb.appname" default="we:kb"/>: account?</a></small>
            </g:form>
      </div>
    </div>
  </div>

<div id="infoModal" class="qmodal modal fade modal-wide" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>

                <h3 class="modal-title">Not yet registered for a <g:message code="gokb.appname" default="we:kb"/>: account?</h3>
            </div>

            <div class="modal-body">Contact us at laser@hbz-nrw.de so that we can set up an account for you and provide you with your initial login information.</div>

            <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
            </div>
        </div>
    </div>
</div>
</body>
</html>
