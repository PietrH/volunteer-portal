<g:set var="isAvailableForTranscription" value="${task?.isAvailableForTranscription(userId)}"/>
<g:if test="${isAdmin || isAvailableForTranscription}">
    <a href="${createLink(controller: 'transcribe', action: 'showTaskWithId', params: [projectId: project?.id, taskId: task?.id])}"
       class="btn btn-primary" role="button">
        <g:message code="overview.transcribe"/>
    </a>
</g:if>
<g:if test="${!isAvailableForTranscription}">
    <vpf:taskTopicButton task="${task}" class="btn-info"/>
</g:if>