<%@ page contentType="text/html; charset=UTF-8" %>
<ul class="dropdown-menu">
    <li class="hidden-xs">
        <div class="navbar-login logged-in">
            <div class="row">
                <div class="col-lg-4">
                    <p class="text-center">
                        <a class="external" target="_blank" id="gravatarLink" title="${message(code:"avatar.customise.label")}">
                            <img src="//www.gravatar.com/avatar/${cl.showCurrentUserEmail().toLowerCase().encodeAsMD5()}?s=80"
                                 class="img-circle img-responsive avatar"/>
                        </a>
                    </p>
                </div>

                <div class="col-lg-8">
                    <p class="text-left word-wrap-break-word"><strong>${cl.showCurrentUserName()}</strong><br/><a
                            href="#">${cl.showCurrentUserEmail()}</a></p>
                </div>
            </div>
        </div>
    </li>
    <li class="divider hidden-xs"></li>
    <li>
        <div class="navbar-login navbar-login-session">
            <div class="row">
                <div class="col-lg-12">
                    <ul class="profile-links">
                        <li><a href="${grailsApplication.config.userDetails.public_url}/myprofile" class="" target="_blank"><g:message code="action.viewProfile" /></a></li>
                        <li><a href="${g.createLink(controller: 'user', action: 'notebook')}" class=""><g:message code="action.notebook" /> <span class="hidden unread-count label label-danger label-as-badge"></span></a></li>
                        <li><a href="${g.createLink(controller: 'logout', action: 'logout', params: [casUrl: "${grailsApplication.config.security.cas.logoutUrl}", appUrl: "${grailsApplication.config.grails.serverURL}"])}" class=""><g:message code="action.logout" /></a></li>
                    </ul>
                </div>
            </div>
        </div>
    </li>
</ul>
