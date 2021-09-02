<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="au.org.ala.volunteer.TemplateField" %>

<g:set var="templateFields"
       value="${TemplateField.findAllByTemplate(task?.project?.template)?.collectEntries {
           [it.fieldType.toString(), it]
       }}"/>

<div class="row">
    <div class="col-sm-12">
        <div class="task-summary thumbnail">
            <table class="table table-condensed table-striped table-hover">
                <thead>
                <tr>
                    <th><g:message code="forum.task_summary.field"/></th>
                    <th><g:message code="forum.task_summary.name"/></th>
                </tr>
                </thead>
                <tbody>
                <g:each in="${fields.sort { it.name }}" var="field">
                    <g:if test="${!field.superceded && field.value}">
                        <tr>
                            <td>${templateFields[field.name]?.label ?: field.name == "catalogNumber" ? message(code: "transcribe.templateViews.all.catalogue_no") : field.name}</td>
                            <td>${field.value}</td>
                        </tr>
                    </g:if>
                </g:each>
                </tbody>
            </table>
        </div>
    </div>
</div>