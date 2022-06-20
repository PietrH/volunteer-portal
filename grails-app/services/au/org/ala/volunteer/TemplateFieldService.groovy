package au.org.ala.volunteer

import grails.converters.JSON
import grails.plugins.csv.CSVWriter
import org.springframework.web.multipart.MultipartFile

import javax.servlet.http.HttpServletResponse

class TemplateFieldService {

    def importFieldsFromCSV(Template template, MultipartFile file, String locale) {

        if (!template || !file) {
            return
        }

        def existingFields = TemplateField.findAllByTemplate(template)

        InputStream is = file.inputStream;
        is.toCsvReader(['charset':'UTF-8']).eachLine { String[] tokens ->
            def field = existingFields.find {f -> f.fieldType.equals(tokens[0] as DarwinCoreField)}
            if (field == null) {
                field = new TemplateField(template: template)
            }

            if (tokens.size() == 0) {
                // Skip empty lines
                return;
            }

            if (tokens.size() < 9) {
                throw new RuntimeException("CSV doesn't have enough columns on all lines.");
            }

            field.fieldType = tokens[0] as DarwinCoreField
            field.defaultValue = tokens[2]
            field.category = tokens[3] as FieldCategory
            field.type = tokens[4] as FieldType
            field.mandatory = tokens[5] as Boolean
            field.multiValue = tokens[6] as Boolean
            field.validationRule = tokens[8]
            field.displayOrder = tokens[9] ? Integer.parseInt(tokens[9]) : null
            field.layoutClass = tokens[10]

            if (field.label == null) {
                field.label = new Translation(locale, tokens[1])
            } else {
                field.label[locale] = tokens[1]
            }
            if (field.helpText == null) {
                field.helpText = new Translation(locale, tokens[7])
            } else {
                field.helpText[locale] = tokens[7]
            }

            field.save(flush: true)
        }

    }

    def exportFieldToCSV(Template templateInstance, HttpServletResponse response) {

        if (!templateInstance) {
            return
        }

        response.setHeader("Content-Disposition", "attachment;filename=fields.txt");
        response.addHeader("Content-type", "text/plain")
        log.warn("Character encoding is ${response.characterEncoding}")

        def writer = new CSVWriter( (Writer) response.writer,  {
            'fieldType' { it.fieldType?.toString() }
            'label' { it.label ?: '' }
            'defaultValue' { it.defaultValue ?: '' }
            'category' { it.category ?: '' }
            'type' { it.type?.toString() }
            'mandatory' { it.mandatory ? "1" : "0" }
            'multiValue' { it.multiValue ? "1" : "0" }
            'helpText' { it.helpText ?: '' }
            'validationRule' { it.validationRule ?: '' }
            'displayOrder' { it.displayOrder ?: ''}
            'layoutClass' { it.layoutClass ?: ''}
        })

        writer.writeHeadings = false

        def fields = TemplateField.findAllByTemplate(templateInstance)?.sort { it.displayOrder }
        for (def field : fields) {
            writer << field
        }
        response.writer.flush()
    }
}
