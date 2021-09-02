<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="au.org.ala.volunteer.TaskStatus" %>

<div class="col-sm-12 col-md-6 col-lg-4">
    <div class="thumbnail">
        <div class="quickPreview">
            <img src="${task?.multimedia?.first()?.filePathToThumbnail}" alt="task thumbnail"/>
            <g:set var="status" value="${task?.status(userId)}"/>
            <g:if test="${status != TaskStatus.open}">
                <div class="task-card-status">${message(code: status.i18nLabel)}</div>
            </g:if>
        </div>

        <div class="caption">
            <div class="actions">
                <g:render template="action" model="${[task: task, project: project, userId: userId, isAdmin: isAdmin]}"/>
                <button id="${task?.id}" class="btn btn-primary btnPreview" role="button">
                    <g:message code="overview.preview"/>
                </button>
            </div>
        </div>
    </div>
</div>