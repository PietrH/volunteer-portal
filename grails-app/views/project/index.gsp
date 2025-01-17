<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="au.org.ala.volunteer.User; au.org.ala.volunteer.Task" %>
<%@ page import="au.org.ala.volunteer.Project" %>
<%@ page import="au.org.ala.volunteer.FieldSyncService" %>
<g:set var="tasksDone" value="${tasksTranscribed ?: 0}"/>
<g:set var="tasksTotal" value="${taskCount ?: 0}"/>
<g:set var="bgImage" value="${projectInstance.backgroundImage}" />
<sitemesh:parameter name="includeBack" value="${true}"/>
<sitemesh:parameter name="includeBackGrey" value="${!(bgImage as Boolean)}"/>
<sitemesh:parameter name="backHref" value="${projectInstance.institutionId ? createLink(controller: 'institution', action: 'index', id: projectInstance.institutionId) : createLink(controller: 'project', action: 'list')}" />
<html xmlns="http://www.w3.org/1999/html">
<head>

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="digivol-expedition"/>
    <title><cl:pageTitle title="${(projectInstance.i18nName?.toString() ?: message(code: "project.default.title")) + (projectInstance.institutionName ? " : ${projectInstance.institutionName}" : '')}"/></title>
    <content tag="primaryColour">${projectInstance.institution?.themeColour}</content>
    <cl:googleMapsScript callback="onGmapsReady" if="${projectInstance.showMap}"/>

    <style type="text/css">

    <g:if test="${bgImage}">
        .a-feature.expedition {
        <g:if test="${projectInstance.backgroundImageOverlayColour}">
            background-image: linear-gradient(${projectInstance.backgroundImageOverlayColour}, ${projectInstance.backgroundImageOverlayColour}), url(${bgImage});
        </g:if>
        <g:else>
            background-image: url(${bgImage});
        </g:else>
        }

    </g:if>
    </style>
</head>

<body class="digivol expedition-landing">

<g:set var="oldClass" value="${bgImage ? '' : 'grey'}" />
<div class="a-feature expedition ${bgImage ? '' : 'old'}">
    <div class="container">
        <div class="row">
            <cl:messages />
            <div class="col-sm-12">
                <div class="logo-holder">
                    <img src="<cl:institutionLogoUrl id="${projectInstance.institution?.id?:-1}"/>" class="img-responsive institution-logo-main">
                </div>
            </div>
        </div>
        <div class="row">
            <div class="col-sm-8">
                <h1>${projectInstance.i18nName}<g:if test="${projectInstance.archived}"> <small><span class="label label-info"><g:message code="project.status.archived" /></span></small></g:if><g:if test="${projectInstance.inactive}"> <small><span class="label label-warning"><g:message code="project.inactive" /></span></small></g:if></h1>
                <div id="projectDescription" class="hidden">
                    <p>${raw(projectInstance.i18nDescription?.toString())}</p><!-- i18nDescriptiontion -->
                    <a href="#" title="read more" class="readmore"><g:message code="project.read_more" /> »</a>
                </div>
                <div class="cta-primary">
                    <g:if test="${percentComplete < 100}">
                        <g:if test="${projectInstance.hasOverviewPage}">
                            <a href="${createLink(controller: 'overview', action: 'index', id: projectInstance.id)}" class="btn btn-primary btn-lg" role="button"><g:message code="project.get_started" /> <span class="glyphicon glyphicon-arrow-right"></span></a>
                        </g:if>
                        <g:else>
                            <a href="${createLink(controller: 'transcribe', action: 'index', id: projectInstance.id)}" class="btn btn-primary btn-lg" role="button"><g:message code="project.get_started" /> <span class="glyphicon glyphicon-arrow-right"></span></a>
                        </g:else>
                        <g:if test="${projectInstance.i18nTutorialLinks}">
                            <a href="${(projectInstance.i18nTutorialLinks?.toString() ? '#tutorial' : createLink(controller: 'tutorials', action: 'index'))}" class="btn btn-lg btn-hollow ${oldClass} tutorial"><g:message code="project.view_tutorial" /></a>
                            <div id="tutorialContent" class="hidden">${raw(projectInstance.i18nTutorialLinks?.toString())}</div>
                        </g:if>
                        <g:else>
                            <a href="${createLink(controller: 'tutorials', action: 'index')}" class="btn btn-lg btn-hollow ${oldClass}  tutorial"><g:message code="project.view_tutorial" /></a>
                        </g:else>
                    </g:if>
                    <g:else>
                        <a class="btn btn-primary btn-lg btn-complete" disabled="disabled" href="#" role="button"><g:message code="project.expedition_complete" /> <span class="glyphicon glyphicon-ok"></span></a>
                        <a href="${g.createLink(controller:"project", action:"list", params: [q: "tag:" + projectInstance.projectType?:'' ])}" class="btn btn-lg btn-hollow ${oldClass} "><g:message code="project.see_similar_expeditions" /></a>
                    </g:else>
                </div>
                <a href="${createLink(controller: 'forum', action: 'projectForum', params: [projectId: projectInstance.id])}" class="forum-link"><g:message code="project.visit_project_forum" /> »</a>
            </div>
            <div class="col-sm-4">
                <g:if test="${!bgImage}">
                    <img src="${projectInstance.featuredImage}" alt="expedition icon" title="${projectInstance.i18nName}" class="thumb-old img-responsive">
                </g:if>
                <div class="projectActionLinks" >
                    <cl:isLoggedIn>
                        <cl:ifAdmin>
                            <g:link class="btn btn-warning " controller="task"
                                    action="projectAdmin" id="${projectInstance.id}"><g:message code="project.admin" /></g:link>&nbsp;
                            <g:link class="btn btn-warning " controller="project"
                                    action="edit" id="${projectInstance.id}"><i
                                    class="icon-cog icon-white"></i> <g:message code="project.settings" /></g:link>&nbsp;
                        </cl:ifAdmin>
                    </cl:isLoggedIn>
                    <cl:ifValidator project="${projectInstance}">
                        <g:link class="btn btn-default btn-hollow grey" controller="task" action="projectAdmin"
                                id="${projectInstance.id}"><g:message code="project.validate_tasks" /></g:link>
                    </cl:ifValidator>
                </div>
            </div>
        </div>

        <g:if test="${bgImage}">
            <div class="row">
                <div class="col-sm-12 image-origin">
                    <p><g:if test="${projectInstance.backgroundImageAttribution}"><g:message code="image.attribution.prefix" /> ${projectInstance.backgroundImageAttribution}</g:if></p>
                </div>
            </div>
        </g:if>
    </div>

    <div class="progress-summary">
        <div class="container">
            <div class="row">
                <div class="col-sm-6">
                    <g:render template="projectSummaryProgressBar" model="${[projectSummary: projectSummary]}"/>
                </div>

                <div class="col-sm-3 col-xs-6">
                    <h3><b>${transcriberCount}</b><g:message code="project.volunteers" /></h3>
                </div>

                <div class="col-sm-3 col-xs-6">
                    <h3><b>${projectInstance.tasks?.size()}</b><g:message code="project.tasks" /></h3>
                </div>
            </div>
        </div>
    </div>
</div>

<g:if test="${projectInstance.showMap}">
    <section id="record-locations">
        <div class="container">
            <div class="row">
                <div class="col-sm-4">
                    <div class="map-header">
                        <h2 class="heading"><g:message code="project.record_locations" /></h2>
                        <p><g:message code="project.record_locations.description" args="${ [projectInstance.i18nName] }" /></p>
                    </div>
                </div>
            </div>
        </div>

        <div id="recordsMap"></div>
    </section>
</g:if>

<section id="main-content">
    <div class="container">
        <div class="row">
            <div class="col-sm-8">
                <div class="row">
                    <div class="col-sm-12">
                        <h2 class="heading">
                            <g:message code="project.expedition_volunteers" />
                        </h2>
                    </div>
                </div>

                <g:if test="${roles.find{it.members?.size()}}">
                    <div class="expedition-team">
                        <div class="row">
                            <g:each in="${roles}" status="i" var="role">
                                <g:set var="roleIcon" value="${role.icons[0]}"/>
                                <div class="col-xs-3 col-sm-2 roleIcon">
                                    <img src='<g:resource file="${roleIcon?.icon}"/>' width="100" height="99" class="img-responsive" title="${roleIcon?.name}" alt="${roleIcon?.name}">
                                </div>
                                <div class="col-xs-9 col-sm-4 roleList">
                                    <h3>${message(code: role.label)}</h3>
                                    <ul>
                                        <g:each in="${role.members}" var="member">
                                            <li><a href="${createLink(controller: 'user', action: 'show', id: member.id, params: [projectId: projectInstance.id])}">${member.name} (${member.count})</a>
                                            </li>
                                        </g:each>
                                    </ul>
                                </div>
                            </g:each>
                        </div>
                    </div>
                </g:if>
                <g:else>
                    <g:message code="project.no_transcriptions_recorded" />
                </g:else>
            </div>

            <div class="col-sm-4">
                %{-- mini leaderboard --}%
                <g:render template="/leaderBoard/stats" model="[disableStats: true, disableHonourBoard: true, projectId: projectInstance.id, maxContributors: 2]"/>
            </div>
        </div>
    </div>
</section>
<asset:javascript src="markerclusterer.js" asset-defer=""/>
<asset:javascript src="dotdotdot" asset-defer=""/>
<asset:javascript src="bootbox" asset-defer=""/>
<g:if test="${projectInstance.showMap}">
    <asset:script type="text/javascript">

        var map, infowindow;

        if (gmapsReady) {
          loadMap();
        } else {
          $(window).on('digivol.gmapsReady', function() {
            loadMap();
          });
        }

        function loadMap() {

            var mapElement = $("#recordsMap");

            if (!mapElement) {
                return;
            }

            var myOptions = {
                scaleControl: true,
                center: new google.maps.LatLng(${projectInstance.mapInitLatitude ?: -24.766785},${projectInstance.mapInitLongitude ?: 134.824219}), // defaults to centre of Australia
                zoom: ${projectInstance.mapInitZoomLevel ?: 3},
                minZoom: 1,
                streetViewControl: false,
                scrollwheel: false,
                mapTypeControl: true,
                mapTypeControlOptions: {
                    style: google.maps.MapTypeControlStyle.DROPDOWN_MENU
                },
                navigationControl: true,
                navigationControlOptions: {
                    style: google.maps.NavigationControlStyle.SMALL // DEFAULT
                },
                mapTypeId: google.maps.MapTypeId.ROADMAP
            };

            map = new google.maps.Map(document.getElementById("recordsMap"), myOptions);
            infowindow = new google.maps.InfoWindow();
            // load markers via JSON web service
            var tasksJsonUrl = "${createLink(controller: "project", action: 'tasksToMap', id: params.id)}";
            $.get(tasksJsonUrl, {}, drawMarkers);
        }

        function drawMarkers(data) {

            if (data) {
                //var bounds = new google.maps.LatLngBounds();
                var markers = [];
                $.each(data, function (i, task) {
                    var latlng = new google.maps.LatLng(task.lat, task.lng);
                    var marker = new google.maps.Marker({
                        position: latlng,
                        //map: map,
                        title: "record: " + (task.cat || task.filename),
                        icon: BVP_JS_URLS.singleMarkerPath
                    });
                    markers.push(marker);
                    google.maps.event.addListener(marker, 'click', function () {
                        infowindow.setContent("[loading...]");
                        // load info via AJAX call
                        load_content(marker, task.id);
                    });
                    //bounds.extend(latlng);
                }); // end each
                var markerCluster = new MarkerClusterer(map, markers, { maxZoom: 18, imagePath: BVP_JS_URLS.markersPath });

                //map.fitBounds(bounds);  // breaks with certain data so removing for now TODO: fix properly
            }
        }

        function load_content(marker, id) {
            $.ajax({

                url: "${createLink(controller: 'task', action: 'details')}/" + id,
                success: function (data) {
                    var content = "<div style='font-size:12px;line-height:1.3em;'>Catalogue No.: " + data.cat + "<br/>Taxon: " + data.name + "<br/>Transcribed by: " + data.transcriber + "</div>";
                    infowindow.close();
                    infowindow.setContent(content);
                    infowindow.open(map, marker);
                }
            });
        }

        function resizeMap() {
            var mapDiv = $("#recordsMap");
            if (mapDiv) {
                var newSize = $('#sidebarDiv').width() - 20;
                mapDiv.css("max-width", "" + newSize + "px")
                mapDiv.css("max-height", "" + newSize + "px")
                mapDiv.css("width", "" + newSize + "px")
                mapDiv.css("height", "" + newSize + "px")
            }
        }
    </asset:script>
</g:if>
<asset:script type="text/javascript">
$(document).ready(function () {

    $("#btnShowIconSelector").click(function(e) {
        e.preventDefault();
        showIconSelector();
    });

    /*
     * Truncate the project description text
     */
    var descriptionDiv = "#projectDescription";
    $(descriptionDiv).removeClass("hidden"); // prevent content jumping
    $(descriptionDiv).dotdotdot({
        after: "a.readmore",
        height: 200,
        callback: function( isTruncated, orgContent ) {
            console.log("isTruncated", isTruncated);
            if (!isTruncated) {
                $("a.readmore").addClass("hidden");
            }
        },
    });
    // read more link to show full description
    $("a.readmore").click(function(e) {
        e.preventDefault();
        var content = $(descriptionDiv).triggerHandler("originalContent");
        $(descriptionDiv).trigger("destroy");
        $(descriptionDiv).html( content );
        $(descriptionDiv + " a.readmore").addClass('hidden');
    });

    // Show tutorial modal if content is present
    $(".tutorial").click(function(e) {
        if ($(this).attr('href') == "#tutorial") {
            e.preventDefault();
            var content = $("#tutorialContent").html();
            bootbox.alert(content);
        }

    });
});

function showIconSelector() {
    bvp.showModal({
        url: "${createLink(action: 'projectLeaderIconSelectorFragment', id: projectInstance.id)}",
                    width:800,
                    height:500,
                    title: 'Select Expedition Leader Icon'
    });
}

</asset:script>
</body>
</html>