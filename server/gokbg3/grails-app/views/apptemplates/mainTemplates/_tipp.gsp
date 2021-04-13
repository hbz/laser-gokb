<%@ page import="de.wekb.helper.RCConstants" %>
%{--<g:set var="editable"
       value="${d.isEditable() && ((request.curator != null ? request.curator.size() > 0 ? true : false : true) || (params.curationOverride == 'true' && request.user.isAdmin()))}"/>--}%
<dl class="dl-horizontal">

    <dt>
        <gokb:annotatedLabel owner="${d}" property="name">Title</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="name"/>
    </dd>


    <sec:ifAnyGranted roles="ROLE_ADMIN">
        <g:if test="${controllerName != 'create'}">
            <dt>
                <gokb:annotatedLabel owner="${d}" property="title">Title</gokb:annotatedLabel>
            </dt>
            <dd style="max-width:60%">
                    <g:link controller="resource" action="show"
                            id="${d.title?.class?.name + ':' + d.title?.id}">
                        ${(d.title?.name) ?: 'Empty'}
                    </g:link>
                <g:if test="${d.title}">(${d.title.niceName})</g:if> (Only to see for ROLE_ADMIN )
            </dd>
        </g:if>
    </sec:ifAnyGranted>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="package">Package</gokb:annotatedLabel>
    </dt>
    <dd>
        <g:if test="${controllerName == 'create'}">
            <gokb:manyToOneReferenceTypedown owner="${d}" field="pkg" baseClass="org.gokb.cred.Package" />
        </g:if>
        <g:elseif test="${controllerName != 'public'}">
            <g:link controller="resource" action="show"
                    id="${d.pkg?.class?.name + ':' + d.pkg?.id}">
                ${(d.pkg?.name) ?: 'Empty'}
            </g:link>
        </g:elseif>
        <g:else>
            ${(d.pkg?.name) ?: 'Empty'}
        </g:else>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="platform">Platform</gokb:annotatedLabel>
    </dt>
    <dd>
        <g:if test="${controllerName == 'create'}">
            <gokb:manyToOneReferenceTypedown owner="${d}" field="hostPlatform" baseClass="org.gokb.cred.Platform" />
        </g:if>
        <g:elseif test="${controllerName != 'public'}">
            <g:link controller="resource" action="show"
                    id="${d.hostPlatform?.class?.name + ':' + d.hostPlatform?.id}">
                ${(d.hostPlatform?.name) ?: 'Empty'}
            </g:link>
        </g:elseif>
        <g:else>
            ${(d.hostPlatform?.name) ?: 'Empty'}
        </g:else>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="url">Host Platform URL</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="url"/>
        <g:if test="${d.url}">
            &nbsp;<a href="${d.url}" target="new"><i class="fas fa-external-link-alt"></i></a>
        </g:if>

    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="publicationType">Publication Type</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="publicationType" config="${RCConstants.TIPP_PUBLICATION_TYPE}"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="medium">Medium</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="medium" config="${RCConstants.TIPP_MEDIUM}"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="language">Language</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="language" config="${RCConstants.KBCOMPONENT_LANGUAGE}"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="firstAuthor">First Author</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="firstAuthor"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="publisherName">Publisher Name</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="publisherName"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="dateFirstInPrint">Date first in print</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" type="date"
                        field="dateFirstInPrint"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="dateFirstOnline">Date first online</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" type="date"
                        field="dateFirstOnline"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="accessStartDate">Access Start Date</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" type="date"
                        field="accessStartDate"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="accessEndDate">Access End Date</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" type="date"
                        field="accessEndDate"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="volumeNumber">Volume Number</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="volumeNumber"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="editionStatement">Edition</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="editionStatement"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}"
                             property="parentPublicationTitleId">Parent publication title ID</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="parentPublicationTitleId"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}"
                             property="precedingPublicationTitleId">Preceding publication title ID</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="precedingPublicationTitleId"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="reference">Reference</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="reference"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="editStatus">Edit Status</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="editStatus"
                               config="${RCConstants.KBCOMPONENT_EDIT_STATUS}"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="status">Status</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="status"
                               config="${RCConstants.KBCOMPONENT_STATUS}"/>
    </dd>


    <dt>
        <gokb:annotatedLabel owner="${d}" property="lastChangedExternal">Last change</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditable  owner="${d}" field="lastChangedExternal" type='date'/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="format">Format</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="format"
                               config="${RCConstants.TIPP_FORMAT}"/>
    </dd>
    <dt>
        <gokb:annotatedLabel owner="${d}" property="paymentType">Payment Type</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="paymentType"
                               config="${RCConstants.TIPP_PAYMENT_TYPE}"/>
    </dd>

    <dt>
        <gokb:annotatedLabel owner="${d}" property="accessType">Access Type</gokb:annotatedLabel>
    </dt>
    <dd>
        <gokb:xEditableRefData owner="${d}" field="accessType"
                               config="${RCConstants.TIPP_ACCESS_TYPE}"/>
    </dd>

</dl>

<ul id="tabs" class="nav nav-tabs">

    <g:if test="${d.publicationType?.value == 'Serial'}">
        <li class="active">
            <a href="#tippcoverage" data-toggle="tab">Coverage</a>
        </li>
    </g:if>
    <li><a href="#identifiers" data-toggle="tab">Identifiers <span
            class="badge badge-warning">${d?.getCombosByPropertyNameAndStatus('ids', 'Active')?.size() ?: '0'}</span>
    </a>
    </li>

    <li>
        <a href="#addprops" data-toggle="tab">Additional Properties
            <span class="badge badge-warning">${d.additionalProperties?.size() ?: '0'}</span>
        </a>
    </li>
    <g:if test="${controllerName != 'public'}">
        <li>
            <a href="#review" data-toggle="tab">Review Requests
                <span class="badge badge-warning">${d.reviewRequests?.size() ?: '0'}</span>
            </a>
        </li>
    </g:if>

    <li>
        <a href="#subjectArea" data-toggle="tab">Subject Area</a>
    </li>
    <li>
        <a href="#series" data-toggle="tab">Series</a>
    </li>
    <li>
        <a href="#prices" data-toggle="tab">Prices
            <span class="badge badge-warning">${d.prices?.size() ?: '0'}</span>
        </a>
    </li>
</ul>


<div id="my-tab-content" class="tab-content">

    <g:if test="${d.publicationType?.value == 'Serial'}">
        <div class="tab-pane active" id="tippcoverage">
            <dl class="dl-horizontal">
                <dt>
                    <gokb:annotatedLabel owner="${d}" property="coverage">Coverage</gokb:annotatedLabel>
                </dt>
                <dd>
                    <table class="table table-striped">
                        <thead>
                        <tr>
                            <th>Start Date</th>
                            <th>Start Volume</th>
                            <th>Start Issue</th>
                            <th>End Date</th>
                            <th>End Volume</th>
                            <th>End Issue</th>
                            <th>Embargo</th>
                            <th>Note</th>
                            <th>Depth</th>
                            <th>Actions</th>
                        </tr>
                        </thead>
                        <tbody>
                        <g:if test="${d.coverageStatements?.size() > 0}">
                            <g:each var="cs" in="${d.coverageStatements.sort { it.startDate }}">
                                <tr>
                                    <td><gokb:xEditable  owner="${cs}" type="date"
                                                        field="startDate"/></td>
                                    <td><gokb:xEditable  owner="${cs}"
                                                        field="startVolume"/></td>
                                    <td><gokb:xEditable  owner="${cs}"
                                                        field="startIssue"/></td>
                                    <td><gokb:xEditable  owner="${cs}" type="date"
                                                        field="endDate"/></td>
                                    <td><gokb:xEditable  owner="${cs}" field="endVolume"/></td>
                                    <td><gokb:xEditable  owner="${cs}" field="endIssue"/></td>
                                    <td><gokb:xEditable  owner="${cs}" field="embargo"/></td>
                                    <td><gokb:xEditable  owner="${cs}" field="coverageNote"/></td>
                                    <td><gokb:xEditableRefData owner="${cs}" field="coverageDepth"
                                                               config="${RCConstants.TIPPCOVERAGESTATEMENT_COVERAGE_DEPTH}"/>
                                    </td>
                                    <td><g:if test="${editable}"><g:link controller="ajaxSupport"
                                                                         action="deleteCoverageStatement"
                                                                         params="[id: cs.id, fragment: 'tippcoverage']">Delete</g:link></g:if></td>
                                </tr>
                            </g:each>
                        </g:if>
                        <g:else>
                            <tr><td colspan="8"
                                    style="text-align:center">${message(code: 'tipp.coverage.empty', default: 'No coverage defined')}</td>
                            </tr>
                        </g:else>
                        </tbody>
                    </table>
                    <g:if test="${editable}">
                        <button
                                class="hidden-license-details btn btn-default btn-sm btn-primary "
                                data-toggle="collapse" data-target="#collapseableAddCoverageStatement">
                            Add new <i class="fas fa-plus"></i>
                        </button>
                        <dl id="collapseableAddCoverageStatement" class="dl-horizontal collapse">
                            <g:form controller="ajaxSupport" action="addToCollection"
                                    class="form-inline" params="[fragment: 'tippcoverage']">
                                <input type="hidden" name="__context"
                                       value="${d.class.name}:${d.id}"/>
                                <input type="hidden" name="__newObjectClass"
                                       value="org.gokb.cred.TIPPCoverageStatement"/>
                                <input type="hidden" name="__recip" value="owner"/>
                                <dt class="dt-label">Start Date</dt>
                                <dd>
                                    <input class="form-control" type="date" name="startDate"/>
                                </dd>
                                <dt class="dt-label">Start Volume</dt>
                                <dd>
                                    <input class="form-control" type="text" name="startVolume"/>
                                </dd>
                                <dt class="dt-label">Start Issue</dt>
                                <dd>
                                    <input class="form-control" type="text" name="startIssue"/>
                                </dd>
                                <dt class="dt-label">End Date</dt>
                                <dd>
                                    <input class="form-control" type="date" name="endDate"/>
                                </dd>
                                <dt class="dt-label">End Volume</dt>
                                <dd>
                                    <input class="form-control" type="text" name="endVolume"/>
                                </dd>
                                <dt class="dt-label">End Issue</dt>
                                <dd>
                                    <input class="form-control" type="text" name="endIssue"/>
                                </dd>
                                <dt class="dt-label">Embargo</dt>
                                <dd>
                                    <input class="form-control" type="text" name="embargo"/>
                                </dd>
                                <dt class="dt-label">Coverage Depth</dt>
                                <dd>
                                    <gokb:simpleReferenceTypedown name="coverageDepth"
                                                                  baseClass="org.gokb.cred.RefdataValue"
                                                                  filter1="${RCConstants.TIPPCOVERAGESTATEMENT_COVERAGE_DEPTH}"/>
                                </dd>
                                <dt class="dt-label">Coverage Note</dt>
                                <dd>
                                    <input class="form-control" type="text" name="coverageNote"/>
                                </dd>
                                <dt></dt>
                                <dd>
                                    <button type="submit"
                                            class="btn btn-default btn-primary btn-sm ">Add</button>
                                </dd>
                            </g:form>
                        </dl>
                    </g:if>
                </dd>
                <dt>
                    <gokb:annotatedLabel owner="${d}" property="coverageNote">Coverage Note</gokb:annotatedLabel>
                </dt>
                <dd>
                    <gokb:xEditable  owner="${d}" field="coverageNote"/>
                </dd>
                <dt>
                    <gokb:annotatedLabel owner="${d}" property="coverageDepth">Coverage Depth</gokb:annotatedLabel>
                </dt>
                <dd>
                    <gokb:xEditableRefData owner="${d}" field="coverageDepth"
                                           config="${RCConstants.TIPP_COVERAGE_DEPTH}"/>
                </dd>
            </dl>
        </div>
    </g:if>


    <div class="tab-pane" id="identifiers">
        <dl>
            <dt>
                <gokb:annotatedLabel owner="${d}" property="ids">Identifiers</gokb:annotatedLabel>
            </dt>
            <dd>
                <g:render template="/apptemplates/secondTemplates/combosByType"
                          model="${[d: d, property: 'ids', fragment: 'identifiers', combo_status: 'Active', cols: [
                                  [expr: 'toComponent.namespace.value', colhead: 'Namespace'],
                                  [expr: 'toComponent.value', colhead: 'ID', action: 'link']]]}"/>
                <g:if test="${editable}">
                    <h4>
                        <gokb:annotatedLabel owner="${d}"
                                             property="addIdentifier">Add new Identifier</gokb:annotatedLabel>
                    </h4>
                    <g:render template="/apptemplates/secondTemplates/addIdentifier"
                              model="${[d: d, hash: '#identifiers']}"/>
                </g:if>
            </dd>
        </dl>

    </div>


    <div class="tab-pane" id="addprops">
        <g:render template="/apptemplates/secondTemplates/addprops"
                  model="${[d: d]}"/>
    </div>

    <g:if test="${controllerName != 'public'}">
        <div class="tab-pane" id="review">
            <g:render template="/apptemplates/secondTemplates/revreqtab" model="${[d: d]}"/>
        </div>
    </g:if>

    <div class="tab-pane" id="subjectArea">
        <dl class="dl-horizontal">
            <dt>
                <gokb:annotatedLabel owner="${d}" property="subjectArea">Subject Area</gokb:annotatedLabel>
            </dt>
            <dd>
                <gokb:xEditable owner="${d}" field="subjectArea"/>
            </dd>
        </dl>
    </div>

    <div class="tab-pane" id="series">

        <dl class="dl-horizontal">
            <dt>
                <gokb:annotatedLabel owner="${d}" property="series">Series</gokb:annotatedLabel>
            </dt>
            <dd>
                <gokb:xEditable owner="${d}" field="series"/>
            </dd>
        </dl>
    </div>
    <g:render template="/tabTemplates/showPrices" model="${[d: d]}"/>
</div>
<g:render template="/apptemplates/secondTemplates/componentStatus"
          model="${[d: d]}"/>

