package au.org.ala.volunteer

import au.org.ala.cas.util.AuthenticationCookieUtils
import com.google.common.base.Stopwatch
import grails.converters.JSON
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.commons.io.FileUtils
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import static javax.servlet.http.HttpServletResponse.*

class ProjectController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST",
                             archive: "POST",
                             wizardImageUpload: "POST", wizardClearImage: "POST", wizardAutosave: "POST", wizardCreate: "POST"]

    static final LABEL_COLOURS = ["label-success", "label-warning", "label-danger", "label-info", "label-primary", "label-default"]
    public static final int MAX_BACKGROUND_SIZE = 512 * 1024

    def taskService
    def fieldService
    def logService
    def userService
    def exportService
    def collectionEventService
    def localityService
    def projectService
    def picklistService
    def projectStagingService
    def projectTypeService
    def authService

    /**
     * Project home page - shows stats, etc.
     */
    def index() {
        def projectInstance = Project.get(params.id)

        String currentUserId = null

        def username = AuthenticationCookieUtils.getUserName(request)
        if (username) currentUserId = authService.getUserForEmailAddress(username)?.userId

        if (!projectInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'project.label', default: 'Project'), params.id])}"
            redirect(action: "list")
        } else {
            // project info
            def taskCount = Task.countByProject(projectInstance)
            def tasksTranscribed = Task.countByProjectAndFullyTranscribedByIsNotNull(projectInstance)
            def userIds = taskService.getUserIdsAndCountsForProject(projectInstance, new HashMap<String, Object>())
            def expedition = grailsApplication.config.expedition
            def roles = [] //  List of Map
            // copy expedition data structure to "roles" & add "members"
            expedition.each {
                def row = it.clone()
                row.put("members", [])
                roles.addAll(row)
            }

            userIds.each {
                // iterate over each user and assign to a role.
                def userId = it[0]
                def count = it[1]
                def assigned = false
                def user = User.findByUserId(userId)
                if (user) {
                    roles.eachWithIndex { role, i ->
                        if (count >= role.threshold && role.members.size() < role.max && !assigned) {
                            // assign role
                            def details = userService.detailsForUserId(userId)
                            def userMap = [name: details.displayName, id: user.id, count: count, userId: user.userId]
                            role.get("members").add(userMap)
                            assigned = true
                            log.debug("assigned: " + userId)
                        } else {
                            log.debug("not assigned: " + userId)
                        }
                    }
                }
            }
            log.debug "roles = ${roles as JSON}"

            def leader = roles.find { it.name == "Expedition Leader" } ?.members.getAt(0)
            def newsItems = NewsItem.findAllByProject(projectInstance, [sort:'created', order:'desc', max: 1])

            def newsItem = null
            if (newsItems) {
                newsItem = newsItems?.first()
            }

            def projectSummary = projectService.makeSummaryListFromProjectList([projectInstance], params)?.projectRenderList?.get(0)

            def percentComplete = (taskCount > 0) ? ((tasksTranscribed / taskCount) * 100) : 0
            if (percentComplete > 99 && taskCount != tasksTranscribed) {
                // Avoid reporting 100% unless the transcribed count actually equals the task count
                percentComplete = 99;
            }

            def recentTasks = Task.findAllByProjectAndFullyTranscribedByIsNotNull(projectInstance, [sort:'dateLastUpdated', order:'desc'])

            render(view: "index", model: [
                    projectInstance: projectInstance,
                    taskCount: taskCount,
                    tasksTranscribed: tasksTranscribed,
                    recentTasks: recentTasks,
                    roles:roles,
                    newsItem: newsItem,
                    currentUserId: currentUserId,
                    leader: leader,
                    percentComplete: percentComplete,
                    newsItems: newsItems,
                    projectSummary: projectSummary,
                    transcriberCount: userIds.size()
            ])
        }
    }

    /**
     * REST web service to return a list of tasks with coordinates to show on Google Map
     */
    def tasksToMap() {

        def projectInstance = Project.get(params.id)
        def taskListFields = []

        if (projectInstance) {
            long startQ  = System.currentTimeMillis();
            def taskList = Task.findAllByProjectAndFullyTranscribedByIsNotNull(projectInstance, [sort:"id", max:999])

            if (taskList.size() > 0) {
                def lats = fieldListToMap(fieldService.getLatestFieldsWithTasks("decimalLatitude", taskList, params))
                def lngs = fieldListToMap(fieldService.getLatestFieldsWithTasks("decimalLongitude", taskList, params))
                def cats = fieldListToMap(fieldService.getLatestFieldsWithTasks("catalogNumber", taskList, params))
                long endQ  = System.currentTimeMillis();
                log.debug("DB query took " + (endQ - startQ) + " ms")
                log.debug("List sizes: task = " + taskList.size() + "; lats = " + lats.size() + "; lngs = " + lngs.size())
                taskList.eachWithIndex { tsk, i ->
                    def jsonObj = [:]
                    jsonObj.put("id",tsk.id)
                    jsonObj.put("filename",tsk.externalIdentifier)
                    jsonObj.put("cat", cats[tsk.id])

                    if (lats.containsKey(tsk.id) && lngs.containsKey(tsk.id)) {
                        jsonObj.put("lat",lats.get(tsk.id))
                        jsonObj.put("lng",lngs.get(tsk.id))
                        taskListFields.add(jsonObj)
                    }
                }

                long endJ  = System.currentTimeMillis();
                log.debug("JSON loop took " + (endJ - endQ) + " ms")
                log.debug("Method took " + (endJ - startQ) + " ms for " + taskList.size() + " records")
            }
            render taskListFields as JSON
        } else {
            // no project found
            render(message(code: 'project.no_project_found') + params.id) as JSON
        }
    }

    /**
     * Output list of email addresses for a given project
     */
    def mailingList() {
        def projectInstance = Project.get(params.id)

        if (projectInstance && userService.isAdmin()) {
            def userIds = taskService.getUserIdsForProject(projectInstance)
            log.debug("userIds = " + userIds)
            def userEmails = userService.getEmailAddressesForIds(userIds)
            //render(userIds)
            def list = userEmails.join(";\n")
            render(text:list, contentType: "text/plain")
        }
        else if (projectInstance) {
            render("You do not have permission to access this page.")
        }
        else {
            render("No project found for id: " + params.id)
        }
    }

    /**
     * Utility to convert list of Fields to a Map with task.id as key
     *
     * @param fieldList
     * @return
     */
    private Map fieldListToMap(List fieldList) {
        Map fieldMap = [:]
        fieldList.each {
            if (it.value) {
                fieldMap.put(it.task.id, it.value)
            }
        }

        return fieldMap
    }

    /**
     * Utility to convert list of Fields to a Map of Maps with task.id as key
     *
     * @param fieldList
     * @return
     */
    private static Map fieldListToMultiMap(List fieldList) {
        Map taskMap = [:]

        fieldList.each {
            if (it.value) {
                Map fm = null;

                if (taskMap.containsKey(it.task.id)) {
                    fm = taskMap.get(it.task.id)
                } else {
                    fm = [:]
                    taskMap[it.task.id] = fm
                }

                Map valueMap = null;
                if (fm.containsKey(it.name)) {
                   valueMap = fm[it.name]
                } else {
                    valueMap = [:]
                    fm[it.name] = valueMap
                }

                valueMap[it.recordIdx] = it.value
            }
        }

        return taskMap
    }

    /**
     * Produce an export file
     */
    def exportCSV() {
        def projectInstance = Project.get(params.id)
        boolean transcribedOnly = params.transcribed?.toBoolean()
        boolean validatedOnly = params.validated?.toBoolean()

        if (projectInstance) {
            def taskList
            if (transcribedOnly) {
                taskList = Task.findAllByProjectAndFullyTranscribedByIsNotNull(projectInstance, [sort:"id", max:9999])
            } else if (validatedOnly) {
                taskList = Task.findAllByProjectAndIsValid(projectInstance, true, [sort:"id", max:9999])
            } else {
                taskList = Task.findAllByProject(projectInstance, [sort:"id", max:9999])
            }
            def taskMap = fieldListToMultiMap(fieldService.getAllFieldsWithTasks(taskList))
            def fieldNames =  ["taskID", "taskURL", "validationStatus", "transcriberID", "validatorID", "externalIdentifier", "exportComment", "dateTranscribed", "dateValidated"]
            fieldNames.addAll(fieldService.getAllFieldNames(taskList))

            Closure export_func = exportService.export_default
            if (params.exportFormat == 'zip') {
                export_func = exportService.export_zipFile
            }

//            def exporter_func_property = exportService.metaClass.getProperties().find() { it.i18nName == 'export_' + projectInstance.template.i18nName }
//            if (exporter_func_property) {
//                export_func = exporter_func_property.getProperty(exportService)
//            }

            if (export_func) {
                response.setHeader("Cache-Control", "must-revalidate");
                response.setHeader("Pragma", "must-revalidate");
                export_func(projectInstance, taskList, taskMap, fieldNames, response)
            } else {
                throw new Exception("No export function for template ${projectInstance.template.name}!")
            }

        }
        else {
            throw new Exception("No project found for id: " + params.id)
        }
    }

    def deleteTasks() {
        def projectInstance = Project.get(params.id)
        projectService.deleteTasksForProject(projectInstance, true)
        redirect(action: "edit", id: projectInstance?.id)
    }

    def list() {
        params.max = Math.min(params.max ? params.int('max') : 24, 1000)

        params.sort = params.sort ?: session.expeditionSort ? session.expeditionSort : 'completed'

        def projectSummaryList = projectService.getProjectSummaryList(params)

        def numberOfUncompletedProjects = projectSummaryList.numberOfIncompleteProjects < 20 ? message(code: "project.numbers."+projectSummaryList.numberOfIncompleteProjects) : "" + projectSummaryList.numberOfIncompleteProjects;

        session.expeditionSort = params.sort;

        [
            projects: projectSummaryList.projectRenderList,
            filteredProjectsCount: projectSummaryList.matchingProjectCount,
            numberOfUncompletedProjects: numberOfUncompletedProjects,
            totalUsers: User.countByTranscribedCountGreaterThan(0)
        ]
    }

    def create() {
        def currentUser = userService.currentUserId
        if (currentUser != null && userService.isAdmin()) {
            def projectInstance = new Project()
            projectInstance.properties = params

            def eventCollectionCodes = [""]
            eventCollectionCodes.addAll(collectionEventService.getCollectionCodes())

            def localityCollectionCodes = [""]
            localityCollectionCodes.addAll(localityService.getCollectionCodes())

            def picklistInstitutionCodes = [""]
            picklistInstitutionCodes.addAll(picklistService.getInstitutionCodes())

            return [projectInstance: projectInstance, templateList: Template.list(), eventCollectionCodes: eventCollectionCodes, localityCollectionCodes: localityCollectionCodes, picklistInstitutionCodes: picklistInstitutionCodes]
        } else {
            flash.message = "You do not have permission to view this page"
            redirect(controller: "project", action: "index", id: params.id)
        }
    }

    def save() {
        def projectInstance = new Project(params)

        if (!projectInstance.template) {
            flash.message = "Please select a template before continuing!"
            render(view: "create", model: [projectInstance: projectInstance])
            return
        }

        if (!projectInstance.i18nName) {
            flash.message = "You must supply a featured label!"
            render(view: "create", model: [projectInstance: projectInstance])
            return
        }

        if (projectInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'project.label', default: 'Project'), projectInstance.id])}"
            redirect(action: "index", id: projectInstance.id)
        } else {
            render(view: "create", model: [projectInstance: projectInstance])
        }

    }

    /**
     * Redirects a image for the supplied project
     */
    def showImage() {
        def projectInstance = Project.get(params.id)
        if (projectInstance) {
            params.max = 1
            def task = Task.findByProject(projectInstance, params)
            if (task?.multimedia?.filePathToThumbnail) {
                redirect(url: grailsApplication.config.server.url + task?.multimedia?.filePathToThumbnail.get(0))
            }
        }
    }

    def show() {
        def projectInstance = Project.get(params.id)
        if (!projectInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'project.label', default: 'Project'), params.id])}"
            redirect(action: "list")
        } else {
            redirect(action:'index', id: projectInstance.id)
        }
    }

    boolean checkAdminForProject(Project project) {
        if(!projectService.isAdminForProject(project)) {
            flash.message = "You do not have permission to view this page"
            redirect(controller: "project", action: "index", id: params.id)
            return false
        }
        return true
    }

    boolean checkAdmin() {
        if(!userService.isAdmin()) {
            flash.message = "You do not have permission to view this page"
            redirect(url: "/")
            return false
        }
        return true
    }

    boolean checkExist(Project project) {
        if (!project) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'project.label', default: 'Project'), params.id])}"
            redirect(action: "list")
            return false
        }
        return true
    }
    
    def edit() {
        def project = Project.get(params.int("id"))

        if (!checkAdminForProject(project)) { 
            return 
        }
        
        redirect(action:"editGeneralSettings", params: params)
    }

    def editGeneralSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }

        final insts = Institution.list()
        //final names = insts*.i18nName.toString()
        final names = insts.collect{ it.i18nName.toString() }
        final nameToId = insts.collectEntries { ["${it.i18nName}": it.id] }
        final labelCats = Label.withCriteria { projections { distinct 'category' } }

        final sortedLabels = project.labels.sort { a,b -> def x = a.category?.compareTo(b.category); return x == 0 ? a.value.compareTo(b.value) : x }
        def counter = 0
        final catColourMap = labelCats.collectEntries { [(it): LABEL_COLOURS[counter++ % LABEL_COLOURS.size()]] }
        return [projectInstance: project, templates: Template.listOrderByName(), projectTypes: ProjectType.listOrderByName(), institutions: names, institutionsMap: nameToId, labelColourMap: catColourMap, sortedLabels: sortedLabels]
    }

    def editTutorialLinksSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }

        return [projectInstance: project, templates: Template.list(), projectTypes: ProjectType.list() ]
    }

    def editPicklistSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }

        def picklistInstitutionCodes = [""]
        picklistInstitutionCodes.addAll(picklistService.getInstitutionCodes())

        return [projectInstance: project, picklistInstitutionCodes: picklistInstitutionCodes ]
    }

    def editMapSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        return [projectInstance: project ]
    }

    def editBannerImageSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        return [projectInstance: project]
    }

    def editBackgroundImageSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        return [projectInstance: project]
    }

    def editTaskSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        def taskCount = Task.countByProject(project)
        return [projectInstance: project, taskCount: taskCount]
    }

    def editNewsItemsSettings() {
        def project = Project.get(params.int("id"))
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        def newsItems = NewsItem.findAllByProject(project, [sort:'created', order:'desc'])
        return [projectInstance: project, newsItems: newsItems]
    }

    def updateGeneralSettings() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }

        if (params.name) {
            params.featuredLabel = params.name
        }

        final instId = params.getLong("institutionId")
        def inst
        if (instId && (inst = Institution.get(instId))) {
            project.institution = inst
        } else {
            project.institution = null
        }

        if (!saveProjectSettingsFromParams(project, params)) {
            render(view: "editGeneralSettings", model: [projectInstance: project])
        } else {
            redirect(action: 'editGeneralSettings', id: project.id)
        }
    }

    def update() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        if (!saveProjectSettingsFromParams(project, params)) {
            render(view: "editGeneralSettings", model: [projectInstance: project])
        } else {
            redirect(action: 'editGeneralSettings', id: project.id)
        }
    }

    def updateTutorialLinksSettings() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        if (!saveProjectSettingsFromParams(project, params)) {
            def newsItems = NewsItem.findAllByProject(project, [sort: 'created', order: 'desc'])
            render(view: "editTutorialLinksSettings", model: [projectInstance: project, newsItems: newsItems])
        } else {
            redirect(action: 'editTutorialLinksSettings', id: project.id)
        }
    }

    def updateNewsItemsSettings() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        if (!saveProjectSettingsFromParams(project, params)) {
            render(view: "editNewsItemsSettings", model: [projectInstance: project])
        } else {
            redirect(action: 'editNewsItemsSettings', id: project.id)
        }
    }


    def deleteAllTasksFragment() {
        def project = Project.get(params.int("id"))
        checkAdminForProject(project)
        def taskCount = Task.countByProject(project)
        [projectInstance: project, taskCount: taskCount]
    }

    def deleteProjectFragment() {
        def project = Project.get(params.int("id"))
        checkAdminForProject(project)
        def taskCount = Task.countByProject(project)
        [projectInstance: project, taskCount: taskCount]
    }

    private boolean saveProjectSettingsFromParams(Project project, GrailsParameterMap params) {
        if (project) {
            if (params.version) {
                def version = params.version.toLong()
                if (project.version > version) {
                    project.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'project.label', default: 'Project')] as Object[], "Another user has updated this Project while you were editing")
                    return false
                }
            }
            project.properties = params
            project.save(flush: true)

            if (!project.hasErrors() && project.save(flush: true)) {
                flash.message = message(code: "project.backend.expedition_updated")
                return true
            }
        }
        return false
    }

    def updatePicklistSettings() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        if (!saveProjectSettingsFromParams(project, params)) {
            render(view: "editPicklistSettings", model: [projectInstance: project])
        } else {
            redirect(action: 'editPicklistSettings', id: project.id)
        }
    }

    def delete() {
        def project = Project.get(params.id)
        if (!checkExist(project) || !checkAdminForProject(project)) {
            return
        }
        try {
            projectService.deleteProject(project)
            flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'project.label', default: 'Project'), params.id])}"
            redirect(action: "list")
        } catch (DataIntegrityViolationException e) {
            flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'project.label', default: 'Project'), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def uploadFeaturedImage() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        def projectInstance = Project.get(params.id)

        projectInstance.featuredImageCopyright = params.featuredImageCopyright
        projectInstance.save(flush: true)

        if(request instanceof MultipartHttpServletRequest) {
            MultipartFile f = ((MultipartHttpServletRequest) request).getFile('featuredImage')
            
            if (f != null && f.size > 0) {

                def allowedMimeTypes = ['image/jpeg', 'image/png']
                if (!allowedMimeTypes.contains(f.getContentType())) {
                    flash.message = "Image must be one of: ${allowedMimeTypes}"
                    render(view:'editBannerImageSettings', model:[projectInstance:projectInstance])
                    return;
                }

                try {
                    def filePath = "${grailsApplication.config.images.home}/project/${projectInstance.id}/expedition-image.jpg"
                    def file = new File(filePath);
                    file.getParentFile().mkdirs();
                    f.transferTo(file);
                    projectService.checkAndResizeExpeditionImage(projectInstance)
                } catch (Exception ex) {
                    flash.message = "Failed to upload image: " + ex.message;
                    render(view:'editBannerImageSettings', model:[projectInstance:projectInstance])
                    return;
                }
            }
        }

        flash.message = "Expedition image settings updated."
        redirect(action: "editBannerImageSettings", id: params.id)
    }

    def uploadBackgroundImage() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        def projectInstance = Project.get(params.id)

        projectInstance.backgroundImageAttribution = params.backgroundImageAttribution
        projectInstance.backgroundImageOverlayColour = params.backgroundImageOverlayColour
        projectInstance.save(flush: true)

        if(request instanceof MultipartHttpServletRequest) {
            MultipartFile f = ((MultipartHttpServletRequest) request).getFile('backgroundImage')

            if (f != null && f.size > 0) {

                def allowedMimeTypes = ['image/jpeg', 'image/png']
                if (!allowedMimeTypes.contains(f.getContentType())) {
                    flash.message = "Image must be one of: ${allowedMimeTypes}"
                    render(view:'editBackgroundImageSettings', model:[projectInstance:projectInstance])
                    return;
                }

                if (f.size >= MAX_BACKGROUND_SIZE) {
                    flash.message = "Image size cannot be bigger than 512 KB (half a MB)"
                    render(view:'editBackgroundImageSettings', model:[projectInstance:projectInstance])
                    return;
                }

                try {
                    f.inputStream.withCloseable {
                        projectInstance.setBackgroundImage(it, f.contentType)
                    }
                } catch (Exception ex) {
                    flash.message = "Failed to upload image: " + ex.message;
                    render(view:'editBackgroundImageSettings', model:[projectInstance:projectInstance])
                    return;
                }
            }
        }

        flash.message = "Background image settings updated."
        redirect(action: "editBackgroundImageSettings", id: params.id)
    }


    def clearBackgroundImageSettings() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        Project projectInstance = Project.get(params.id)
        if (projectInstance) {
            projectInstance.backgroundImageAttribution = null
            projectInstance.backgroundImageOverlayColour = null
            projectInstance.setBackgroundImage(null,null)
        }

        flash.message = "Background image settings have been deleted."
        redirect(action: "editBackgroundImageSettings", id: params.id)
    }

    def resizeExpeditionImage() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        def projectInstance = Project.get(params.int("id"))
        if (projectInstance) {
            projectService.checkAndResizeExpeditionImage(projectInstance)
        }
        redirect(action:'edit', id:projectInstance?.id)
    }

    def setLeaderIconIndex() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        if (params.id) {
            def project = Project.get(params.id)
            if (project) {
                def iconIndex = params.int("iconIndex")?:0
                def role = grailsApplication.config.expedition[0]
                def icons = role.icons
                if (iconIndex >= 0 && iconIndex < icons.size()) {
                    project.leaderIconIndex = iconIndex
                    project.save()
                }
            }
        }

        redirect(action: "index", id: params.id)
    }

    def projectLeaderIconSelectorFragment() {
        def projectInstance = Project.get(params.getInt("id"))
        def expeditionConfig = grailsApplication.config.expedition
        // find the leader role from the config map
        def role = expeditionConfig.find { it.name == "Expedition Leader"}
        [projectInstance: projectInstance, role: role]
    }

    def updateMapSettings() {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
        }

        def projectInstance = Project.get(params.int("id"))
        if (projectInstance) {
            def showMap = params.showMap == "on"
            def zoom = params.int("mapZoomLevel")
            def latitude = params.double("mapLatitude")
            def longitude = params.double("mapLongitude")

            projectInstance.showMap = showMap

            if (zoom && latitude && longitude) {
                projectInstance.mapInitZoomLevel = zoom
                projectInstance.mapInitLatitude = latitude
                projectInstance.mapInitLongitude = longitude
            }
            projectInstance.save(flush: true)

            flash.message = message(code:"project.backend.map_settings_updated")
        }
        redirect(action:'editMapSettings', id:projectInstance?.id)
    }

    def ajaxFeaturedOwnerList() {
        def c = Project.createCriteria()
        def results = c {
            ilike("featuredOwner", "%${params.q ?: ''}%")
            projections {
                distinct("featuredOwner")
            }
        }
        render(results as JSON)
    }

    def findProjectFragment() {

    }

    def findProjectResultsFragment() {

        def query = "%${params.q as String}%" ?: ""

        def projectList = (List<Project>)Project.createCriteria().list {
            or {
                i18nName {
                    ilike WebUtils.getCurrentLocaleAsString(), query
                }
                i18nDescription {
                    ilike WebUtils.getCurrentLocaleAsString(), query
                }
                i18nShortDescription {
                    ilike WebUtils.getCurrentLocaleAsString(), query
                }
            }
        }

        [projectList: projectList]
    }

    def addLabel(Project projectInstance) {
        def labelId = params.labelId
        def label = Label.get(labelId)
        if (!label) {
            render status: 404
            return
        }

        projectInstance.addToLabels(label)
        projectInstance.save(flush: true)
        // Just adding a label won't trigger the GORM update event, so force a project update
        DomainUpdateService.scheduleProjectUpdate(projectInstance.id)
        render status: 204
    }

    def removeLabel(Project projectInstance) {
        def labelId = params.labelId
        def label = Label.get(labelId)
        if (!label) {
            render status: 404
            return
        }

        projectInstance.removeFromLabels(label)
        // Just adding a label won't trigger the GORM update event, so force a project update
        DomainUpdateService.scheduleProjectUpdate(projectInstance.id)
        render status: 204
    }

    def newLabels(Project projectInstance) {
        def term = params.term ?: ''
        def ilikeTerm = "%${term.replace('%','')}%"
        def existing = projectInstance?.labels
        def labels

        if (existing) {
            def existingIds = existing*.id.toList()
            labels = Label.withCriteria {
                or {
                    ilike 'category', ilikeTerm
                    ilike 'value', ilikeTerm
                }
                not {
                    inList 'id', existingIds
                }
            }
        } else {
            labels = Label.findAllByCategoryIlikeOrValueIlike(ilikeTerm, ilikeTerm)
        }

        render labels as JSON
    }

    def wizard(String id) {
        if(!checkAdmin()) {
            return
        }

        if (!id) {
            def stagingId = UUID.randomUUID().toString()
            projectStagingService.ensureStagingDirectoryExists(stagingId)
            redirect(action: 'wizard', id: stagingId)
            return
        }

        if (!projectStagingService.stagingDirectoryExists(id)) {
            // this one has probably been cancelled or saved already
            redirect(action: 'wizard')
            return
        }

        def project = new NewProjectDescriptor(stagingId: id)

        def list = Institution.list()
        def institutions = list.collect { [id: it.id, name: it.i18nName.toString() ] }
        def templates = Template.listOrderByName([:])
        def projectTypes = ProjectType.listOrderByName([:])
        projectTypes.each() { ProjectType t ->
            t.name = message(code: t.label)
        }


        def projectImageUrl = projectStagingService.hasProjectImage(project) ? projectStagingService.getProjectImageUrl(project) : null
        def labels = Label.list()
        def autosave = projectStagingService.getTempProjectDescriptor(id)

        def c = PicklistItem.createCriteria();
        def picklistInstitutionCodes = c {
            isNotNull("institutionCode")
            projections {
                distinct("institutionCode")
            }
            order('institutionCode')
        }


        final labelCats = Label.withCriteria { projections { distinct 'category' } }

        def counter = 0
        final catColourMap = labelCats.collectEntries { [(it): LABEL_COLOURS[counter++ % LABEL_COLOURS.size()]] }

        [
                stagingId: id,
                institutions: institutions,
                templates: templates,
                projectTypes: projectTypes,
                projectImageUrl: projectImageUrl,
                labels: labels,
                autosave: autosave,
                picklists: picklistInstitutionCodes,
                labelColourMap: catColourMap
        ]
    }

    def wizardAutosave(String id) {
        if(!checkAdmin()) {
            return
        }
        projectStagingService.saveTempProjectDescriptor(id, request.reader)
        render status: 204
    }

    def wizardImageUpload(String id) {
        if(!checkAdmin()) {
            return
        }
        def project = new NewProjectDescriptor(stagingId: id)

        def errors = []
        def errorStatus = SC_BAD_REQUEST
        def result

        if (request instanceof MultipartHttpServletRequest) {
            MultipartFile f = ((MultipartHttpServletRequest) request).getFile('image')
            if (f != null && f.size > 0) {
                final allowedMimeTypes = ['image/jpeg', 'image/png']
                if (!allowedMimeTypes.contains(f.getContentType())) {
                    errors << "Image must be one of: ${allowedMimeTypes}"
                    errorStatus = SC_UNSUPPORTED_MEDIA_TYPE
                } else {
                    if (params.type == 'backgroundImageUrl') {
                        if (f.size > MAX_BACKGROUND_SIZE) {
                            errors << message(code: 'project.backend.background_image.size', args: [MAX_BACKGROUND_SIZE/1024])
                            errorStatus = SC_REQUEST_ENTITY_TOO_LARGE
                        } else {
                            projectStagingService.uploadProjectBackgroundImage(project, f)
                            result = projectStagingService.getProjectBackgroundImageUrl(project)
                        }
                    } else {
                        projectStagingService.uploadProjectImage(project, f)
                        result = projectStagingService.getProjectImageUrl(project)
                    }
                }
            } else {
                errors << message(code: 'project.backend.background_image.missing')
            }
        }

        if (errors) {
            response.status = errorStatus
            render(errors as JSON)
        } else {
            render([imageUrl: result] as JSON)
        }
    }

    def wizardClearImage(String id) {
        if(!checkAdmin()) {
            return
        }
        def project = new NewProjectDescriptor(stagingId: id)
        def type = request.getJSON()?.type ?: ''
        if (type == 'background') {
            projectStagingService.clearProjectBackgroundImage(project)
        } else {
            projectStagingService.clearProjectImage(project)
        }
        render status: 204
    }

    def wizardProjectNameValidator(String name) {
        if(!checkAdmin()) {
            return
        }
        render([ count: ((List<Project>)Project.createCriteria().list {
            i18nName {
                like WebUtils.getCurrentLocaleAsString(), name
            }
        }).size() ] as JSON)
    }

    def wizardCancel(String id) {
        projectStagingService.purgeProject(new NewProjectDescriptor(stagingId: id))
        redirect(controller:'admin', action:"index")
    }

    def wizardCreate(String id) {
        if(!checkAdmin()) {
            return
        }

        try {
            def body = request.getJSON()
            body.createdBy = userService.getCurrentUserId()
            def descriptor = NewProjectDescriptor.fromJson(id, body)

            log.debug("Attempting to create project with descriptor: $descriptor")

            def projectInstance = projectStagingService.createProject(descriptor)
            if (!projectInstance) {
                render status: 400
            } else {
                response.status = 201
                def obj = [id: projectInstance.id] as JSON
                render(obj)
            }
        } finally {
            projectStagingService.purgeProject(new NewProjectDescriptor(stagingId: id))
        }
    }

    def archiveList() {
        final sw = Stopwatch.createStarted()
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
            return
        }

        if (!params.sort) {
            params.sort = 'id'
            params.order = 'asc'
        }
        if (!params.max) {
            params.max = 10
        }

        def projects = Project.findAllByArchived(false, params)
        sw.stop()
        log.debug("archiveList: findAllByArchived = $sw")
        sw.reset().start()
        def total = Project.countByArchived(false)
        sw.stop()
        log.debug("archiveList: countByArchived = $sw")
//        sw.reset().start()
//        def sizes = projectService.projectSize(projects)
//        sw.stop()
//        log.debug("archiveList: projectSize = $sw")
        sw.reset().start()
        def completions = projectService.calculateCompletion(projects)
        sw.stop()
        log.debug("archiveList: calculateCompletion = $sw")
        sw.reset().start()

        List<ArchiveProject> projectsWithSize = projects.collect {
            final counts = completions[it.id]
            final transcribed
            final validated
            if (counts) {
                transcribed = (counts.transcribed / counts.total) * 100.0
                validated = (counts.validated / counts.total) * 100.0
            } else {
                transcribed = 0.0
                validated = 0.0
            }
            new ArchiveProject(project: it, /*size: sizes[it.id].size,*/ percentTranscribed: transcribed, percentValidated: validated)
        }

        respond(projectsWithSize, model: ['archiveProjectInstanceListSize': total, 'imageStoreStats': projectService.imageStoreStats()])
    }

    def projectSize(Project project) {
        def size = [size: FileUtils.byteCountToDisplaySize(projectService.projectSize(project).size)]
        respond(size)
    }

    def archive(Project project) {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
            return
        }

        try {
            projectService.archiveProject(project)
            project.archived = true
            log.info("${project.i18nName} (id=${project.id}) archived")
            respond status: SC_NO_CONTENT
        } catch (e) {
            log.error("Couldn't archive project $project", e)
            response.sendError(SC_INTERNAL_SERVER_ERROR, "An error occured while archiving ${project.i18nName}")
        }
    }

    def downloadImageArchive(Project project) {
        if (!userService.isAdmin()) {
            response.sendError(SC_FORBIDDEN, message(code: 'project.backend.permssion'))
            return
        }
        response.contentType = 'application/zip'
        response.setHeader('Content-Disposition', "attachment; filename=\"${project.id}-${project.i18nName}-images.zip\"")
        final os = response.outputStream
        try {
            projectService.writeArchive(project, os)
        } catch (e) {
            log.error("Exception while creating image archive for $project", e)
            //os.close()
        }
    }

    def summary() {
        /*
        {
          "project": "Name of project or expidition",
          "contributors": "Number of individual users",
          "numberOfSubjects": "Number of total assets/specimens/subjects",
          "percentComplete": "0-100",
          "firstContribution": "UTC Timestamp",
          "lastContribution": "UTC Timestamp"
        }
         */
        final Project project
        def id = params.id
        if (id.isLong()) {
            project = Project.get(id as Long)
        } else {
            List<Project> projectList = ((List<Project>)Project.createCriteria().list {
                i18nName {
                    like WebUtils.getCurrentLocaleAsString(), id
                }
            })
            project = projectList.size()>0?projectList.get(0):null;
        }

        if (!project) {
            response.sendError(404, message(code: 'project.backend.project_not_found'))
            return
        }

        def completions = projectService.calculateCompletion([project])[project.id]
        def numberOfSubjects = completions?.total
        def percentComplete = numberOfSubjects > 0 ? ((completions?.transcribed as Double) / ((numberOfSubjects ?: 1.0) as Double)) * 100.0 : 0.0
        def contributors = projectService.calculateNumberOfTranscribers(project)
        def dates = projectService.calculateStartAndEndTranscriptionDates(project)
//        projectService.
        def result = [
                project: project.i18nName,
                contributors: contributors,
                numberOfSubjects: numberOfSubjects,
                percentComplete: percentComplete,
                firstContribution: dates?.start,
                lastContribution: dates?.end
        ]

        respond result
    }

}
