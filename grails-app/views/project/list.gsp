<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="au.org.ala.volunteer.ProjectActiveFilterType; au.org.ala.volunteer.ProjectStatusFilterType; au.org.ala.volunteer.Project" %>

<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="${grailsApplication.config.ala.skin}"/>
    <g:set var="entityName" value="${message(code: 'project.label', default: 'Project')}"/>
    <title><cl:pageTitle title="${g.message(code:"default.list.label", args:['Expedition'])}"/></title>

    <asset:stylesheet src="digivol-image-resize"/>
</head>

<body class="digivol">

    <cl:headerContent title="${message(code:'default.projectlist.label', default: "Volunteer for a virtual expedition")}" selectedNavItem="expeditions">
        <g:message code="project.projectlist.description" args="${ [numberOfUncompletedProjects] }" />
    </cl:headerContent>

    <section id="main-content">
        <div class="container">
            <cl:messages />
            <div class="row">
                <div class="col-sm-8">
                    <div class="row">
                        <div class="col-sm-6">
                            <h2 class="heading">
                                <g:if test="${params.q}">
                                    <g:message code="project.projectlist.expeditions_matching"/>:
                                    <span class="tag currentFilter">
                                        <span>${message(code: params.q.replaceAll('tag:',''))}</span>
                                        <a href="?mode=${params.mode}&q="><i class="remove glyphicon glyphicon-remove-sign glyphicon-white"></i></a>
                                    </span>
                                </g:if>
                                <g:else>
                                    <g:message code="project.projectlist.all_expeditions"/>
                                </g:else>
                                <div class="subheading"><g:message code="project.projectlist.showing_expeditions" args="${ [filteredProjectsCount] }" /></div>
                            </h2>
                        </div>

                        <div class="col-sm-6">
                            <div class="card-filter">
                                <div class="btn-group pull-right" role="group" aria-label="...">
                                    <a href="${createLink(action:'list', params:[mode:'grid'])}" class="btn btn-default btn-xs ${params.mode != 'grid' ? '' : 'active'}"><i class="glyphicon glyphicon-th-large "></i></a>
                                    <a href="${createLink(action:'list')}" class="btn btn-default btn-xs ${params.mode == 'grid' ? '' : 'active'}"><i class="glyphicon glyphicon-th-list"></i></a>
                                </div>

                                <div class="custom-search-input body">
                                    <div class="input-group">
                                        <input type="text" id="searchbox" class="form-control input-lg" placeholder="${message(code: 'main.navigation.search.placeholder')}"/>
                                        <span class="input-group-btn">
                                            <button id="btnSearch" class="btn btn-info btn-lg" type="button">
                                                <i class="glyphicon glyphicon-search"></i>
                                            </button>
                                        </span>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>

                    <div class="row ">
                        <div class="col-sm-12">
                            <g:set var="statusFilterMode" value="${ params.statusFilter ?: ProjectStatusFilterType.showAll}" />
                            <g:set var="activeFilterMode" value="${ params.activeFilter ?: ProjectActiveFilterType.showAll}" />
                            <g:set var="urlParams" value="${[sort: params.sort ?: "", order: params.order ?: "", offset: 0, q: params.q ?: "", mode: params.mode ?: "", statusFilter:statusFilterMode, activeFilter: activeFilterMode]}" />

                            <div class="btn-group pull-right hide" style="padding-right: 10px">
                                <g:each in="${ProjectStatusFilterType.values()}" var="mode">
                                    <g:set var="href" value="?${(urlParams + [statusFilter: mode]).collect { it }.join('&')}" />
                                    <a href="${href}" class="btn btn-small ${statusFilterMode == mode?.toString() ? "active" : ""}">${message(code: mode.i18nLabel)}</a>
                                </g:each>
                            </div>

                            <cl:ifAdmin>
                                <div class="btn-group pull-right" style="padding-right: 10px; margin-bottom: 10px;margin-top: -20px;">
                                    <g:each in="${ProjectActiveFilterType.values()}" var="mode">
                                        <g:set var="href" value="?${(urlParams + [activeFilter: mode]).collect { it }.join('&')}" />
                                        <a href="${href}" class="btn btn-warning btn-small ${activeFilterMode == mode?.toString() ? "active" : ""}">${message(code: mode.i18nLabel)}</a>
                                    </g:each>
                                </div>
                            </cl:ifAdmin>
                        </div>
                    </div>

                    <g:set var="model" value="${[extraParams:[statusFilter: statusFilterMode?.toString(), activeFilter: activeFilterMode?.toString()]]}" />
                    <g:if test="${params.mode == 'grid'}">
                        <g:render template="projectListThumbnailView" model="${model}"/>
                    </g:if>
                    <g:else>
                        <g:render template="ProjectListDetailsView" model="${model}" />
                    </g:else>
                </div>
                <div class="col-sm-4">
                    <g:render template="/leaderBoard/stats"/>
                </div>
            </div>
        </div>
    </section>
<asset:javascript src="digivol-image-resize" asset-defer=""/>
<asset:script>

    $(function() {

        $("#searchbox").keydown(function(e) {
            if (e.keyCode ==13) {
                doSearch();
            }
        });

        $("#btnSearch").click(function(e) {
            e.preventDefault();
            doSearch();
        });

        $("a.fieldHelp").qtip({
            tip: true,
            position: {
                corner: {
                    target: 'topMiddle',
                    tooltip: 'bottomLeft'
                }
            },
            style: {
                width: 400,
                padding: 8,
                background: 'white', //'#f0f0f0',
                color: 'black',
                textAlign: 'left',
                border: {
                    width: 4,
                    radius: 5,
                    color: '#E66542'// '#E66542' '#DD3102'
                },
                tip: 'bottomLeft',
                name: 'light' // Inherit the rest of the attributes from the preset light style
            }
        }).bind('click', function(e) {
            e.preventDefault();
            return false;
        });

        function doSearch() {
            var q = $("#searchbox").val();
            var url = "${createLink(controller: 'project', action: 'list')}?mode=${params.mode}&q=" + encodeURIComponent(q);
                window.location = url;
            }
        });

</asset:script>
</body>
</html>
