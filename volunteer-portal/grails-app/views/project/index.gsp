<%@ page contentType="text/html;charset=UTF-8" import="org.codehaus.groovy.grails.commons.ConfigurationHolder" %>
<%@ page import="au.org.ala.volunteer.Task" %>
<%@ page import="au.org.ala.volunteer.Project" %>
<%@ page import="au.org.ala.volunteer.FieldSyncService" %>
<g:set var="tasksDone" value="${tasksTranscribed?:0}"/>
<g:set var="tasksTotal" value="${taskCount?:0}"/>
<g:set var="tasksDonePercent" value="${(taskCount > 0) ? ((tasksDone / tasksTotal) * 100) : 0}"/>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="main"/>
    <title>Volunteer Portal - Atlas of Living Australia</title>
    <script type='text/javascript' src='https://www.google.com/jsapi'></script>
    <script src="${resource(dir:'js', file:'markerclusterer.js')}" type="text/javascript"></script>
    <script src="http://ajax.googleapis.com/ajax/libs/jqueryui/1.8/jquery-ui.min.js"></script>
    <link href="http://ajax.googleapis.com/ajax/libs/jqueryui/1.8/themes/base/jquery-ui.css" rel="stylesheet" type="text/css"/>
    <script type='text/javascript'>
        google.load('visualization', '1', {packages:['gauge']});

        function loadChart() {
            var data = new google.visualization.DataTable();
            data.addColumn('string', 'Label');
            data.addColumn('number', 'Value');
            data.addRows(3);
            data.setValue(0, 0, '%');
            data.setValue(0, 1, <g:formatNumber number="${tasksDonePercent}" format="#"/>);

            var chart = new google.visualization.Gauge(document.getElementById('recordsChartWidget'));
            var options = {width: 150, height: 150, minorTicks: 5, majorTicks: ["0%","25%","50%","75%","100%"]};
            chart.draw(data, options);
        }

        google.load("maps", "3.3", {other_params:"sensor=false"});
        var map, infowindow;

        function loadMap() {
            var myOptions = {
                scaleControl: true,
                center: new google.maps.LatLng(-24.766785, 134.824219), // centre of Australia
                zoom: 3,
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
            var tasksJsonUrl = "${resource(dir: "project/tasksToMap", file: params.id)}";
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
                        title:"record: " + task.cat
                    });
                    markers.push(marker);
                    google.maps.event.addListener(marker, 'click', function() {
                        infowindow.setContent("[loading...]");
                        // load info via AJAX call
                        load_content(marker, task.id);
                    });
                    //bounds.extend(latlng);
                }); // end each
                var markerCluster = new MarkerClusterer(map, markers, { maxZoom: 18 });

                //map.fitBounds(bounds);  // breaks with certain data so removing for now TODO: fix properly
            }
        }

        function load_content(marker, id){
            $.ajax({
                url: "${resource(dir: "task/details", file: '/')}" + id + ".json",
                success: function(data){
                    var content = "<div style='font-size:12px;line-height:1.3em;'>Catalogue No.: "+data.cat
                            +"<br/>Taxon: "+data.name+"<br/>Transcribed by: "+data.transcriber+"</div>";
                    infowindow.close();
                    infowindow.setContent(content);
                    infowindow.open(map, marker);
                }
            });
        }

        $(document).ready(function() {
            // load chart
            $("#recordsChartWidget").progressbar({ value: <g:formatNumber number="${tasksDonePercent}" format="#"/> });
            //load map
            loadMap();
        });
    </script>
</head>

<body class="two-column-right">
<div class="nav">
  <span class="menuButton"><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></span>
  <span class="menuButton">${projectInstance.name?:'Project'}</span>
</div>
<div class="body">
    <h1>Welcome to the ${projectInstance.name?:'Volunteer Portal'}</h1>
    <g:if test="${flash.message}">
        <script type="text/javascript">
            alert("${flash.message}");
        </script>
    </g:if>
    <div id="projectInfo">
        ${projectInstance.description}
    </div>
    <div class='front-image'>
        <img src="${resource(file: projectInstance.bannerImage)}"/>
    </div>

    <div class='front-buttons buttons-big'>
        <g:link controller="transcribe" id="${projectInstance.id}">
            <img src="${resource(dir: 'images', file: 'start-generic.png')}"/>
        </g:link>
    </div>
    <div class='front-buttons buttons-small'>
        <g:link controller="user" action="project" id="${projectInstance.id}" params="[sort:'transcribedCount',order:'desc']">
            <img src="${resource(dir: 'images', file: 'score.png')}"/>
        </g:link><br/>
        <g:link controller="user" action="myStats" >
            <img src="${resource(dir: 'images', file: 'stats.png')}"/>
        </g:link>
    </div>

    <div id="expedition">
        <div id="personnel">
            <h2>Expedition Personnel</h2>
            <table>
                <thead style="display: none">
                    <tr><td>Role</td><td>Members</td></tr>
                </thead>
                <tbody>
                    <g:each in="${roles}" status="i" var="role">
                        <tr>
                            <td><a href="${role.link}" title="${role.bio}" target="AM"><img src='<g:resource file="${role.icon}"/>' alt="expedition person icon"/></a></td>
                            <td><strong>${role.name}: </strong><cl:listUsersInRole users="${role.members}" projectId="${projectInstance.id}"/></td>
                        </tr>
                    </g:each>
                </tbody>
            </table>
        </div>
        <div id="progress" class="">
            <h2>Expedition Progress</h2>
            <div id="recordsChart">
                Records captured: <span style="white-space: nowrap;">${tasksDone} of ${tasksTotal}</span> (<g:formatNumber number="${tasksDonePercent}" format="#"/>%)
            </div>
            <div id="recordsChartWidget"></div>
            <div style="clear: both"></div>
            <h2>Expedition News</h2>
            <div id="news">
                <g:each in="${recentNewsItems}" var="news" status="i">
                    <p><b>${news.title} (<g:formatDate format="dd-MM-yyyy" date="${news.created}"/>)</b></p>
                    ${news.body}
                </g:each>
            </div>
            <g:if test="${(projectInstance.showMap)}">
                <div id="recordMapLabel"><h4>Showing location of records transcribed to date</h4></div>
                <div id="recordsMap"></div>
            </g:if>
        </div>
    </div>
    <div style="clear: both">&nbsp;</div>
    <cl:isLoggedIn>
        <g:link controller="task" action="projectAdmin" id="${projectInstance.id}" style="color:#DDDDDD;">Admin</g:link>
    </cl:isLoggedIn>
</div>
</body>
</html>