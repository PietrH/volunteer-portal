package au.org.ala.volunteer

import grails.converters.JSON
import org.springframework.web.multipart.MultipartFile
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

class TemplateController {

    static allowedMethods = [save: "POST", update: "POST"]

    def userService
    def templateFieldService
    def templateService

    def index() {
        redirect(action: "list", params: params)
    }

    boolean checkAdmin() {
        if(!userService.isAdmin()) {
            flash.message = "You do not have permission to view this page"
            redirect(url: "/")
            return false
        }
        return true
    }

    def list() {
        if (!checkAdmin()) {
            return
        }
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        [templateInstanceList: Template.list(params), templateInstanceTotal: Template.count()]
    }

    def create() {
        if (!checkAdmin()) {
            return
        }
        def templateInstance = new Template()
        templateInstance.author = userService.currentUserId
        templateInstance.properties = params
        return [templateInstance: templateInstance, availableViews: templateService.getAvailableTemplateViews()]
    }

    def save() {
        if (!checkAdmin()) {
            return
        }
        params.author = userService.currentUserId
        def templateInstance = new Template(params)
        if (templateInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'template.label', default: 'Template'), templateInstance.id])}"
            redirect(action: "edit", id: templateInstance.id)
        }
        else {
            render(view: "create", model: [templateInstance: templateInstance])
        }
    }

    def show() {
        if (!checkAdmin()) {
            return
        }
        def templateInstance = Template.get(params.id)
        if (!templateInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
            redirect(action: "list")
        }
        else {
            [templateInstance: templateInstance]
        }
    }

    def edit() {
        if (!checkAdmin()) {
            return
        }
        def templateInstance = Template.get(params.id)
        if (!templateInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
            redirect(action: "list")
        }
        else {
            def availableViews = templateService.getAvailableTemplateViews()
            return [templateInstance: templateInstance, availableViews: availableViews]
        }
    }

    def update() {
        if (!checkAdmin()) {
            return
        }
        def templateInstance = Template.get(params.id)
        if (templateInstance) {
            if (params.version) {
                def version = params.version.toLong()
                if (templateInstance.version > version) {

                    templateInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'template.label', default: 'Template')] as Object[], "Another user has updated this Template while you were editing")
                    render(view: "edit", model: [templateInstance: templateInstance])
                    return
                }
            }
            templateInstance.properties = params

            def viewParams = JSON.parse(params.viewParamsJSON) as Map

            def newViewParams = new HashMap<String, String>()
            if (viewParams) {
                viewParams.keySet().each {
                    newViewParams[it.toString()] = viewParams[it]?.toString()
                }
            }
            templateInstance.viewParams = newViewParams

            if (!templateInstance.hasErrors() && templateInstance.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: 'template.label', default: 'Template'), templateInstance.id])}"
                redirect(action: "edit", id: templateInstance.id)
            } else {
                render(view: "edit", model: [templateInstance: templateInstance])
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
            redirect(action: "list")
        }
    }

    def delete() {
        if (!checkAdmin()) {
            return
        }
        def templateInstance = Template.get(params.id)
        if (templateInstance) {
            try {
                // First got to delete all the template_fields...
                def fields = TemplateField.findAllByTemplate(templateInstance)
                if (fields) {
                    fields.each { it.delete(flush: true) }
                }
                // Now can delete template proper
                templateInstance.delete(flush: true)

                flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
                redirect(action: "list")
            }
            catch (org.springframework.dao.DataIntegrityViolationException e) {
                flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
                redirect(action: "edit", id: params.id)
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])}"
            redirect(action: "list")
        }
    }

    def manageFields() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        if (templateInstance) {
            def fields = TemplateField.findAllByTemplate(templateInstance)?.sort { it.displayOrder }

            [templateInstance: templateInstance, fields: fields]
        }
    }

    def addTemplateFieldFragment() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        if (templateInstance) {
            def fields = TemplateField.findAllByTemplate(templateInstance)?.sort { it.displayOrder }
            [templateInstance: templateInstance, fields: fields]
        }
    }

    def moveFieldUp() {
        checkAdmin()
        def field = TemplateField.get(params.int("fieldId"))
        if (field) {
            if (field.displayOrder > 1) {
                def predecessor = TemplateField.findByTemplateAndDisplayOrder(field.template, field.displayOrder - 1)
                if (predecessor) {
                    // swap their positions
                    predecessor.displayOrder++
                    predecessor.save()
                }
                field.displayOrder--
                field.save()
            }
        }

        redirect(action:'manageFields', id: field?.template?.id)
    }

    def moveFieldDown() {
        checkAdmin()
        def field = TemplateField.get(params.int("fieldId"))
        if (field) {
            def max = getLastDisplayOrder(field.template)
            if (field.displayOrder < max ) {
                def successor = TemplateField.findByTemplateAndDisplayOrder(field.template, field.displayOrder + 1)
                if (successor) {
                    // swap their positions
                    successor.displayOrder--
                    successor.save()
                }
                field.displayOrder++
                field.save()
            }
        }

        redirect(action:'manageFields', id: field?.template?.id)
    }

    def moveFieldToPosition() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        def field = TemplateField.findByTemplateAndId(templateInstance, params.int("fieldId"))
        def newOrder = params.int("newOrder")
        if (templateInstance && field && newOrder) {
            def fields = TemplateField.findAllByTemplate(templateInstance)?.sort { it.displayOrder }
            if (newOrder >= 0 && newOrder <= fields.size()) {
                fields.each {
                    if (it.displayOrder >= newOrder) {
                        it.displayOrder++
                    }
                }
                field.displayOrder = newOrder
                redirect(action:'cleanUpOrdering', id:templateInstance.id)
                return
            }
        }

        redirect(action:'manageFields', id: field?.template?.id)
    }

    def cleanUpOrdering() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        if (templateInstance) {
            def fields = TemplateField.findAllByTemplate(templateInstance)?.sort { it.displayOrder }
            int i = 1
            fields.each {
                it.displayOrder = i++
            }
        }

        redirect(action:'manageFields', id: templateInstance?.id)
    }

    def addField() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        def fieldType = params.fieldType
        def classifier = params.fieldTypeClassifier

        if (templateInstance && fieldType) {

            def existing = TemplateField.findAllByTemplateAndFieldTypeAndFieldTypeClassifier(templateInstance, fieldType, classifier)
            if (existing && fieldType != DarwinCoreField.spacer.toString() && fieldType != DarwinCoreField.widgetPlaceholder.toString()) {
                flash.message = "Add field failed: Field type " + fieldType + " already exists in this template!"
            } else {
                def displayOrder = getLastDisplayOrder(templateInstance) + 1
                FieldCategory category = params.category ?: FieldCategory.none
                FieldType type = params.type ?: FieldType.text
                def label = params.label ?: ""
                def field = new TemplateField(template: templateInstance, category: category, fieldType: fieldType, fieldTypeClassifier: classifier, displayOrder: displayOrder, defaultValue: '', type: type, label: label)
                field.save(failOnError: true, flush: true)
            }
        }

        redirect(action:'manageFields', id: templateInstance?.id)
    }

    private int getLastDisplayOrder(Template template) {
        def c = TemplateField.createCriteria()
        def results = c({
            eq('template', template)
            projections {
                max('displayOrder')
            }
        })
        def max = results?.getAt(0) ?: 0
        return max
    }

    def deleteField() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))
        def field = TemplateField.findByTemplateAndId(templateInstance, params.int("fieldId"))
        if (field && templateInstance) {
            field.delete(flush: true)
        }
        redirect(action:'manageFields', id: templateInstance?.id)
    }

    def preview() {
        checkAdmin()
        def templateInstance = Template.get(params.int("id"))

        def projectInstance = new Project(template: templateInstance, featuredOwner: "ALA", i18nName: "${templateInstance.name} Preview (${templateInstance.viewName})")
        def taskInstance = new Task(project: projectInstance)
        def multiMedia = new Multimedia(id: 0)
        taskInstance.addToMultimedia(multiMedia)
        def recordValues = [:]
        def imageMetaData = [0: [width: 2048, height: 1433]]

        render(view: '/transcribe/templateViews/' + templateInstance.viewName, model: [taskInstance: taskInstance, recordValues: recordValues, isReadonly: null, template: templateInstance, nextTask: null, prevTask: null, sequenceNumber: 0, imageMetaData: imageMetaData, isPreview: true])
    }

    def exportFieldsAsCSV() {
        checkAdmin()

        def templateInstance = Template.get(params.int("id"))

        if (templateInstance) {
            templateFieldService.exportFieldToCSV(templateInstance, response)
        }

    }

    def importFieldsFromCSV() {
        checkAdmin()

        MultipartFile f = request.getFile('uploadFile')

        if (!f || f.isEmpty()) {
            flash.message = message(code: 'template.file_missing_or_invalid')
        } else {

            def templateInstance = Template.get(params.int("id"))
            if (templateInstance) {
                templateFieldService.importFieldsFromCSV(templateInstance, f, WebUtils.getCurrentLocaleAsString())
            } else {
                flash.message = message(code: 'template.missing_template_id')
            }
        }

        redirect(action:'manageFields', params:[id: params.id])
    }

    def cloneTemplate() {
        checkAdmin()
        def template = Template.get(params.int("templateId"))
        def newName = params.newName

        if (newName) {
            def existing = Template.findByName(newName)
            if (existing) {
                flash.message = message(code: 'template.failed_to_clone_template', args: [newName])
                redirect(action:'list')
                return
            }
        }

        if (template && newName) {

            def newTemplate = new Template(name: newName, viewName: template.viewName, author: userService.currentUser.userId)

            newTemplate.viewParams = [:]
            template.viewParams.keySet().each { key ->
                newTemplate.viewParams[key] = template.viewParams[key]
            }

            newTemplate.save(flush: true, failOnError: true)
            // Now we need to copy over the template fields
            def fields = TemplateField.findAllByTemplate(template)
            fields.each { f ->
                def newField = new TemplateField(f.properties)
                newField.template = newTemplate
                newField.save()
            }
        }

        redirect(action:'list')
    }

    def cloneTemplateFragment() {
        checkAdmin()
        def template = Template.get(params.int("sourceTemplateId"))
        [templateInstance: template]
    }

    def viewParamsForm() {
        checkAdmin()
        def view = (params?.view ?: '') + 'Params'
        try { //if (resExists(view)) {
            render template: view
        } catch (e) { //} else {
            log.trace("Could not render template $view", e)
            render status: 404
        }
    }

    private def resExists(resName) {
        //Not needed : def grailsAttributes = new DefaultGrailsApplicationAttributes(request.servletContext)
        def engine = grailsAttributes.pagesTemplateEngine
        def resUri = grailsAttributes.getTemplateUri(resName, request)
        def resource = engine.getResourceForUri(resUri)
        log.debug "resUri=${resUri}; resource=${resource}; exists=${resource?.exists()}; readable=${resource?.readable}"
        return resource?.readable
    }
}
