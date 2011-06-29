package au.org.ala.volunteer

class UserController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    def taskService
    def authService
    def userService
    def fieldSyncService

    def index = {
        redirect(action: "list", params: params)
    }

    def logout = {
        session.invalidate()
        redirect(url:"${params.casUrl}?url=${params.appUrl}")
    }

    def myStats = {
      userService.registerCurrentUser()
      def currentUser = authService.username()
      def userInstance = User.findByUserId(currentUser)
      redirect(action: "show", id: userInstance.id)
    }

    def list = {
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        if(!params.sort){
          params.sort = params.sort ? params.sort : "displayName"
          params.order = "desc"
        }
        def currentUser = authService.username()
        [userInstanceList: User.list(params), userInstanceTotal: User.count(),currentUser: currentUser]
    }

    def create = {
        def userInstance = new User()
        userInstance.properties = params
        return [userInstance: userInstance]
    }

    def save = {
        def userInstance = new User(params)
        if (userInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'user.label', default: 'User'), userInstance.id])}"
            redirect(action: "show", id: userInstance.id)
        }
        else {
            render(view: "create", model: [userInstance: userInstance])
        }
    }

    def show = {
        def userInstance = User.get(params.id)
        def currentUser = authService.username()
        params.max = Math.min(params.max ? params.int('max') : 12, 100)
        def recentTasks = taskService.getRecentlyTranscribedTasks(userInstance.getUserId(), params)
        def fieldsInTask = [:] // map of task id to saved fields for that task
        recentTasks.each {
            // get the fields as a Map (to be able to display catalogNumber
            fieldsInTask.put(it.id, fieldSyncService.retrieveFieldsForTask(it))
        }

        def numberOfTasksEdited = taskService.getRecentlyTranscribedTasks(userInstance.getUserId(), [:]).size()
        def pointsTotal =  (userInstance.transcribedCount * 100) + ((numberOfTasksEdited - userInstance.transcribedCount ) * 10)

        if (!userInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
            redirect(action: "list")
        }
        else {
            [userInstance: userInstance, taskInstanceList: recentTasks, currentUser: currentUser, numberOfTasksEdited: numberOfTasksEdited,
                    pointsTotal: pointsTotal, fieldsInTask: fieldsInTask]
        }
    }

    def edit = {
        def userInstance = User.get(params.id)
        if (!userInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
            redirect(action: "list")
        }
        else {
            return [userInstance: userInstance]
        }
    }

    def update = {
        def userInstance = User.get(params.id)
        if (userInstance) {
            if (params.version) {
                def version = params.version.toLong()
                if (userInstance.version > version) {
                    
                    userInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'user.label', default: 'User')] as Object[], "Another user has updated this User while you were editing")
                    render(view: "edit", model: [userInstance: userInstance])
                    return
                }
            }
            userInstance.properties = params
            if (!userInstance.hasErrors() && userInstance.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: 'user.label', default: 'User'), userInstance.id])}"
                redirect(action: "show", id: userInstance.id)
            }
            else {
                render(view: "edit", model: [userInstance: userInstance])
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
            redirect(action: "list")
        }
    }

    def delete = {
        def userInstance = User.get(params.id)
        if (userInstance) {
            try {
                userInstance.delete(flush: true)
                flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
                redirect(action: "list")
            }
            catch (org.springframework.dao.DataIntegrityViolationException e) {
                flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
                redirect(action: "show", id: params.id)
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
            redirect(action: "list")
        }
    }
}