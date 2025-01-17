<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="au.org.ala.volunteer.PicklistItem; au.org.ala.volunteer.Picklist" %>
<div class="row">
    <div id="mapWidgets">
        <div id="mapWrapper" class="col-sm-8">
            <div id="mapCanvas"></div>
            <div class="searchHint">
                <br/>
                <i class="fa fa-info-circle"></i> <g:message code="transcribe.geolocationTool.hint" />
            </div>
        </div>

        <div id="mapInfo" class="col-sm-4">

            <h5><g:message code="transcribe.geolocationTool.locality_search" /></h5>
            <div class="custom-search-input in-modal">
                <div class="input-group">
                    <input type="text" name="address" id="address" class="form-control input-lg" placeholder="${message(code: 'transcribe.geolocationTool.search_placeholder')}">
                    <span class="input-group-btn">
                        <button id="locationSearch" class="btn btn-info btn-lg" type="button">
                            <i class="glyphicon glyphicon-search"></i>
                        </button>
                    </span>
                </div>
            </div>

            <h5><g:message code="transcribe.geolocationTool.coordinate_uncertainty" /></h5>
            <label for="infoUncert"><g:message code="transcribe.geolocationTool.adjust_uncertainty" /></label>
            <select class="form-control" id="infoUncert">
                <g:set var="coordinateUncertaintyPL"
                       value="${Picklist.findByName('coordinateUncertaintyInMeters',[order: "asc"])}"/>
                <g:each in="${PicklistItem.findAllByPicklist(coordinateUncertaintyPL)}" var="item">
                    <g:set var="isSelected"><g:if
                            test="${(item.value == '1000')}">selected='selected'</g:if></g:set>
                    <option ${isSelected} value="${item.value}">${item.key ?: item.value}</option>
                </g:each>
            </select>

            <p>
                <g:message code="transcribe.geolocationTool.please_choose_an_uncertainty_value" />
            </p>

            <h5><g:message code="transcribe.geolocationTool.location_data" /></h5>
            <table class="table table-striped">
                <tbody>
                <tr>
                    <th scope="row"><g:message code="transcribe.geolocationTool.latitude" /></th>
                    <td id="infoLat"></td>
                </tr>
                <tr>
                    <th scope="row"><g:message code="transcribe.geolocationTool.longitude" /></th>
                    <td id="infoLng"></td>
                </tr>
                <tr>
                    <th scope="row"><g:message code="transcribe.geolocationTool.location" /></th>
                    <td id="infoLoc"></td>
                </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<asset:script type="text/javascript">

  var map, marker, circle, locationObj, geocoder;
  var quotaCount = 0;

  function initializeGeolocateTool() {
    geocoder = new google.maps.Geocoder();
    var lat = $('.decimalLatitude').val();
    var lng = $('.decimalLongitude').val();
    var coordUncer = $('.coordinateUncertaintyInMeters').val();
    var latLng;

    if (lat && lng && coordUncer) {
      latLng = new google.maps.LatLng(lat, lng);
      $('#infoUncert').val(coordUncer);
    } else {
      latLng = new google.maps.LatLng(${grailsApplication.config.location.default.latitude}, ${grailsApplication.config.location.default.longitude});
    }

    var myOptions = {
      zoom: 10,
      center: latLng,
      scrollwheel: true,
      scaleControl: true,
      mapTypeId: google.maps.MapTypeId.ROADMAP
    };

    var mapCanvas = document.getElementById("mapCanvas");
    if (mapCanvas) {
      map = new google.maps.Map(document.getElementById("mapCanvas"), myOptions);
    }

    marker = new google.maps.Marker({
      position: latLng,
      //map.getCenter(),
      title: 'Specimen Location',
      map: map,
      draggable: true
    });
    // Add a Circle overlay to the map.
    var radius = parseInt($(':input#infoUncert').val());
    circle = new google.maps.Circle({
      map: map,
      radius: radius,
      // 3000 km
      strokeWeight: 1,
      strokeColor: 'white',
      strokeOpacity: 0.5,
      fillColor: '#2C48A6',
      fillOpacity: 0.2
    });
    // bind circle to marker
    circle.bindTo('center', marker, 'position');

    // Add dragging event listeners.
    google.maps.event.addListener(marker, 'dragstart',
      function () {
        updateMarkerAddress('${URLEncoder.encode(message(code: 'transcribe.geolocationTool.dragging'),"UTF8")}');
      });

    google.maps.event.addListener(marker, 'drag',
      function () {
        updateMarkerStatus('${URLEncoder.encode(message(code: 'transcribe.geolocationTool.dragging'),"UTF8")}');
        updateMarkerPosition(marker.getPosition());
      });

    google.maps.event.addListener(marker, 'dragend',
      function () {
        updateMarkerStatus('Drag ended');
        geocodePosition(marker.getPosition());
        map.panTo(marker.getPosition());
      });

    map.panTo(marker.getPosition());

    var localityStr = $(':input.verbatimLocality').val() || '';


    if (!$(':input#address').val()) {
      var latLongRegex = /([-]{0,1}\d+)[^\d](\d+)[^\d](\d+).*?([-]{0,1}\d+)[^\d](\d+)[^\d](\d+)/;
      var match = latLongRegex.exec(localityStr);
      if (match) {
        var interpretedLatLong = match[1] + '�' + match[2] + "'" + match[3] + '" ' + match[4] + '�' + match[5] + "'" + match[6] + '"';
        $(':input#address').val(interpretedLatLong);
      } else {

        var state = $(":input.stateProvince").val();
        if (state) {
          if (localityStr) {
            localityStr += ", " + state;
          } else {
            localityStr = state;
          }
        }

        var country = $(":input.country").val();
        if (country) {
          if (localityStr) {
            localityStr += ", " + country;
          } else {
            localityStr = country;
          }
        }

        $(':input#address').val(localityStr.replace(/\s+/g, ' '));
      }
    }
    if (lat && lng) {
      geocodePosition(latLng);
      updateMarkerPosition(latLng);
    } else if ($('.verbatimLatitude').val() && $('.verbatimLongitude').val()) {
      $(':input#address').val($('.verbatimLatitude').val() + "," + $('.verbatimLongitude').val())
      codeAddress();
    } else if (localityStr) {
      codeAddress();
    }

  }

  /**
   * Google geocode function
   */
  function geocodePosition(pos) {
    geocoder.geocode({
        latLng: pos
      },
      function (responses) {
        if (responses && responses.length > 0) {
          updateMarkerAddress(responses[0].formatted_address, responses[0]);
        } else {
          updateMarkerAddress('${URLEncoder.encode(message(code: 'transcribe.geolocationTool.cannot_determine_address'),"UTF8")}');
        }
      });
  }

  function parse_gps(input){

    // Only parse as coordinates if in some degree/hour/minute N/S/E/W form
    if (!/^([nwse]?\s*(\d+[°'"]\s*){1,3}\s*[nwse]?\s*){2}$/i.test(input)) {
        return input.split(',');
    }

    var parts = input.split(/[°'"]+/).join(' ').split(/[^\w\S]+/);
    var directions = [];
    var coords = [];
    var dd = 0;
    var pow = 0;

    for(i in parts){

        if(isNaN(parts[i])){

            var _float = parseFloat(parts[i]);
            var direction = parts[i];
            if(!isNaN(_float)){
                dd += (_float/Math.pow( 60, pow++));
                direction = parts[i].replace(_float, '');
            }

            direction = direction[0];
            if(direction == 'S' || direction == 'W')
                dd *= -1;

            directions[ directions.length ] = direction;
            coords[ coords.length ] = dd;
            dd = pow = 0;
        } else {
            dd += (parseFloat(parts[i]) / Math.pow(60, pow++));
        }
    }

    if(directions[0] == 'W' || directions[0] == 'E'){
        var tmp = coords[0];
        coords[0] = coords[1];
        coords[1] = tmp;
    }

    return coords;
  }

  /**
   * Reverse geocode coordinates via Google Maps API
   */
  function codeAddress() {
    var address = $(':input#address').val().replace(/\n/g, " ");
    let _pos = null;
    let _parse_gps = parse_gps(address);
    let payload = {};
    if (geocoder && address) {
      //geocoder.getLocations(address, addAddressToPage);
      quotaCount++;

      if(_parse_gps.length > 1){
        _pos = new google.maps.LatLng(_parse_gps[0], _parse_gps[1]);
        payload['latLng'] = _pos;
      }
      else{
        payload['address'] = address;
        payload['region'] = 'BE';
      }

      geocoder.geocode(payload,
        function (results, status) {

          if (status == google.maps.GeocoderStatus.OK) {
            // geocode was successful
            var latLng = results[0].geometry.location;
            var lat = latLng.lat();
            var lon = latLng.lng();
            var locationStr = results[0].formatted_address;
            updateMarkerAddress(locationStr, results[0]);
            updateMarkerPosition(latLng);
            marker.setPosition(latLng);
            map.panTo(latLng);

            if (typeof(geocodeCallback) == "function") {
              geocodeCallback(results[0])
            }

            return true;
          } else {
            // alert("Geocode was not successful for the following reason: " + status + " (count: " + quotaCount + ")");
          }
        });
    }
  }

  function updateMarkerStatus(str) {
    //$(':input.locality').val(str);
  }

  function updateMarkerPosition(latLng) {
    //var rnd = 1000000;
    var precisionMap = {
      100: 1000000,
      1000: 10000,
      10000: 100,
      100000: 10,
      1000000: 1
    };
    var coordUncertainty = $("#infoUncert").val();
    var key = (coordUncertainty) ? coordUncertainty : 1000;
    var rnd;

    if (precisionMap[key]) {
      rnd = precisionMap[key];
    } else {
      if (key > 100000) {
        rnd = 1;
      } else if (key >= 10000) {
        rnd = 10;
      } else if (key >= 5000) {
        rnd = 100;
      } else if (key >= 1000) {
        rnd = 1000;
      } else {
        rnd = 10000;
      }
    }

    // round to N decimal places
    var lat = Math.round(latLng.lat() * rnd) / rnd;
    var lng = Math.round(latLng.lng() * rnd) / rnd;
    $('#infoLat').html(lat);
    $('#infoLng').html(lng);
  }

  function updateMarkerAddress(str, addressObj) {
    $('#infoLoc').html(str);
    // update form fields with location parts
    if (addressObj && addressObj.address_components) {
      var addressComps = addressObj.address_components;
      locationObj = addressComps; // save to global var

      if (typeof(geocodeCallback) == "function") {
        geocodeCallback(addressObj)
      }
    }
  }

  function setLocationFields() {
    //debugger;
    if ($('#infoLat').html() && $('#infoLng').html()) {
      // copy map fields into main form
      var latWidget = $('.decimalLatitude');
      var lngWidget = $('.decimalLongitude');
      var localityWidget = $(".locality");
      if (latWidget.length < 1 || lngWidget.length < 1) {
        // decimal controls do not exist in current template, so try the verbatim ones
        latWidget = $(".verbatimLatitude");
        lngWidget = $(".verbatimLongitude");
      }

// DON'T UNCOMMENT - Never copy over verbatimLocality, even though we sometimes copy over verbatimLatitude and verbatimLongitude
//                if (!localityWidget.length) {
//                    localityWidget =  $(".verbatimLocality");
//                }

      if (latWidget.length > 0 || lngWidget.length > 0) {
        latWidget.val($('#infoLat').html()).trigger("change");
        lngWidget.val($('#infoLng').html()).trigger("change");

        $(':input.coordinateUncertaintyInMeters').val($('#infoUncert').val());
        // locationObj is a global var set from geocoding lookup
        for (var i = 0; i < locationObj.length; i++) {
          var name = locationObj[i].long_name;
          var type = locationObj[i].types[0];
          var hasLocality = false;
          // go through each avail option
          if (type == 'country') {
            //$(':input.countryCode').val(name1);
            $(':input.country').val(name);
          } else if (type == 'locality') {
            localityWidget.val(name);
            hasLocality = true;
          } else if (type == 'administrative_area_level_1') {
            $(':input.stateProvince').val(name);
          }
        }

        // update the verbatimLocality picklist on the server
        var url = VP_CONF.updatePicklistUrl;
        var params = {
          name: $(":input.verbatimLocality").val(),
          lat: latWidget.val(),
          lng: lngWidget.val(),
          cuim: $(':input.coordinateUncertaintyInMeters').val()
        };
        $.getJSON(url, params, function (data) {
          // only interested in return text for debugging problems
          //alert(url + " returned: " + data);
        });
      }

      bvp.hideModal();
    } else {
      alert("${URLEncoder.encode(message(code: 'transcribe.geolocationTool.location_data_is_empty'),"UTF8")}");
    }
  }

  $(document).ready(function () {

    $("#btnClose").click(function (e) {
      e.preventDefault();
      bvp.hideModal();
    });

    // trigger Google geolocation search on search button
    $('#locationSearch').click(function (e) {
      e.preventDefault();
      // ignore the href text - used for data
      codeAddress();
    });

    $('input#address').keypress(function (e) {
      if (e.which == 13) {
        codeAddress();
      }
    });

    // Catch Coordinate Uncertainty select (mapping tool) change
    $('.coordinatePrecision, #infoUncert').change(function (e) {
      var rad = parseInt($(this).val());
      circle.setRadius(rad);
      updateMarkerPosition(marker.getPosition());
    });

    $('#setLocationFields').click(function (e) {
      e.preventDefault();
      setLocationFields();

    });


    function init() {
      var $modal = $('#mapWidgets').parents('.modal');
      if ($modal.length > 0) {
        $modal.on('shown.bs.modal', function () {
          initializeGeolocateTool();
        })
      } else {
        initializeGeolocateTool();
        google.maps.event.trigger(map, "resize");
        setTimeout(function () {
          google.maps.event.trigger(map, "resize");
        }, 500);
      }
    }

    if (gmapsReady) init();
    else {
      $(window).on('digivol.gmapsReady', init);
    }

    bvp.bindTooltips("a.geolocateHelp.fieldHelp", 600);

  }); // End document.ready


</asset:script>
