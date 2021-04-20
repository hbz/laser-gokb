<%@ page import="de.wekb.helper.RCConstants;" %>
<div class="tab-pane" id="openAccess">
    <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
            <dt>
                <gokb:annotatedLabel owner="${d}" property="openAccess">Open Access</gokb:annotatedLabel>
            </dt>
            <dd>
                <gokb:xEditableRefData owner="${d}" field="openAccess" config="${RCConstants.TIPP_OPEN_ACCESS}"/>
            </dd>
        </dl>
    </g:if>
</div>