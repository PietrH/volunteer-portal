package au.org.ala.volunteer

import groovyx.net.http.HttpResponseException
import org.apache.commons.io.IOExceptionWithCause
import org.springframework.web.multipart.MultipartFile
import java.util.regex.Pattern

class TutorialService {

    def grailsApplication

    private String getTutorialDirectory() {
        return new File((String)grailsApplication.config.images.home + "/tutorials").getCanonicalPath()
    }

    private String createFilePath(String name) {
        return tutorialDirectory + "/" + name
    }

    private void verifyInTutorialDirectory(File file) {
        if (!file.getCanonicalPath().startsWith(tutorialDirectory)) {
            throw new IOException("Cannot interact with files outside of " + tutorialDirectory)
        }
    }

    def listTutorials() {
        def dir = new File(tutorialDirectory)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        def files = dir.listFiles()
        def tutorials = []
        files.each {
            def url = grailsApplication.config.server.url + '/' + grailsApplication.config.images.urlPrefix + "/tutorials/" + it.name
            tutorials << [file: it, name: it.name, url: url]
        }

        return tutorials
    }

    def uploadTutorialFile(MultipartFile file) {
        def filePath = createFilePath(file.originalFilename)
        def newFile = new File(filePath)
        verifyInTutorialDirectory(newFile)

        file.transferTo(newFile.absoluteFile);
    }

    def deleteTutorial(String name) {
        def filePath = createFilePath(name)
        def file = new File(filePath)
        verifyInTutorialDirectory(file)

        if (file.exists()) {
            file.delete()
            return true
        }

        return false
    }

    def renameTutorial(String oldname, String newname) {
        def filePath = createFilePath(oldname)
        def file = new File(filePath)
        verifyInTutorialDirectory(file)

        if (file.exists()) {
            def newFile = new File(createFilePath(newname))
            verifyInTutorialDirectory(file)

            if (!newFile.exists()) {
                file.renameTo(newFile)
            }
        }
    }

    def getTutorialGroups() {
        def dir = new File(tutorialDirectory)
        if (!dir.exists()) {
            dir.mkdirs();
        }

        def files = dir.listFiles()
        def tutorials = [:]

        def regex = Pattern.compile("^(.*)_(.*)\$")
        files.each {
            def url = grailsApplication.config.server.url + '/' + grailsApplication.config.images.urlPrefix + "/tutorials/" + it.name
            def group = "-" // no group
            def title = it.name
            def matcher = regex.matcher(it.name)
            if (matcher.matches()) {
                group = matcher.group(1)
                title = matcher.group(2)
            }

            title = title.subSequence(0, title.lastIndexOf('.'))

            if (!tutorials[group]) {
                tutorials[group] = []
            }

            tutorials[group] << [file: it, name: it.name, url: url, title:title]
        }

        if (!tutorials.containsKey('-')) {
            tutorials['-'] = []
        }


        return tutorials
    }

}
