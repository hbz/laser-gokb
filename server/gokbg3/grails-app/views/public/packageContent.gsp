<%@ page import="de.wekb.helper.RCConstants" %>
<!DOCTYPE html>
<html>
<head>
    <meta name='layout' content='public'/>
    <title><g:message code="gokb.appname" default="we:kb"/>: Package Content</title>
</head>

<body>

<div class="container">

    <div class="row">
        <div class="box">

        <div class="col-lg-12 card">

                <g:if test="${flash.error}">
                    <div class="alert alert-warning" style="font-weight:bold;">
                        <p>${flash.error}</p>
                    </div>
                </g:if>


                <g:if test="${pkg}">

                    <h1>Package: <span style="font-weight:bolder;">${pkgName}</span></h1>

                    <g:render template="rightBox"
                              model="${[d: pkg]}"/>

                    <dl class="dl-horizontal">
                        <dt>
                            <gokb:annotatedLabel owner="${pkg}" property="provider">Provider</gokb:annotatedLabel>
                        </dt>
                        <dd>
                            <gokb:manyToOneReferenceTypedown owner="${pkg}" field="provider" baseClass="org.gokb.cred.Org" >${pkg.provider?.name}</gokb:manyToOneReferenceTypedown>
                        </dd>

                        <dt>
                            <gokb:annotatedLabel owner="${pkg}" property="source">Source</gokb:annotatedLabel>
                        </dt>
                        <dd>
                            <gokb:manyToOneReferenceTypedown owner="${pkg}" field="source" baseClass="org.gokb.cred.Source" >${pkg.source?.name}</gokb:manyToOneReferenceTypedown>
                        </dd>

                        <dt>
                            <gokb:annotatedLabel owner="${pkg}" property="nominalPlatform">Nominal Platform</gokb:annotatedLabel>
                        </dt>
                        <dd>
                            <gokb:manyToOneReferenceTypedown owner="${pkg}" field="nominalPlatform"
                                                             name="${comboprop}" baseClass="org.gokb.cred.Platform" >
                                ${pkg.nominalPlatform?.name ?: ''}
                            </gokb:manyToOneReferenceTypedown>
                        </dd>
                        <g:if test="${pkg}">
                            <dt>
                                <gokb:annotatedLabel owner="${pkg}" property="status">Status</gokb:annotatedLabel>
                            </dt>
                            <dd>
                                    ${pkg.status?.value ?: 'Not Set'}
                            </dd>
                        </g:if>

                        <dt> <gokb:annotatedLabel owner="${pkg}" property="lastUpdateComment">Last Update Comment</gokb:annotatedLabel> </dt>
                        <dd> <gokb:xEditable class="ipe" owner="${pkg}" field="lastUpdateComment" /> </dd>

                        <dt> <gokb:annotatedLabel owner="${pkg}" property="editStatus">Edit Status</gokb:annotatedLabel> </dt>
                        <dd> <gokb:xEditableRefData owner="${pkg}" field="editStatus" config="${RCConstants.KBCOMPONENT_EDIT_STATUS}" /> </dd>

                        <dt> <gokb:annotatedLabel owner="${pkg}" property="description">Description</gokb:annotatedLabel> </dt>
                        <dd> <gokb:xEditable class="ipe" owner="${pkg}" field="description" /> </dd>

                        <dt> <gokb:annotatedLabel owner="${pkg}" property="descriptionURL">URL</gokb:annotatedLabel> </dt>
                        <dd> <gokb:xEditable class="ipe" owner="${pkg}" field="descriptionURL" /> </dd>

                        <dt>
                            <gokb:annotatedLabel owner="${pkg}" property="globalNote">Global Range</gokb:annotatedLabel>
                        </dt>
                        <dd>
                            <gokb:xEditable class="ipe" owner="${pkg}" field="globalNote" />
                        </dd>


                        <g:render template="/apptemplates/secondTemplates/refdataprops"
                                  model="${[d: pkg, rd: refdata_properties, dtype: pkg.class.simpleName, notShowProps: [RCConstants.PACKAGE_LIST_STATUS]]}"/>

                    </dl>
                    <ul class="nav nav-tabs" id="myTab" role="tablist">
                        <li class="nav-item" role="presentation">
                            <a class="nav-link active" id="home-tab" data-toggle="tab" href="#home" role="tab" aria-controls="home" aria-selected="true">Home</a>
                        </li>
                        <li class="nav-item" role="presentation">
                            <a class="nav-link" id="profile-tab" data-toggle="tab" href="#profile" role="tab" aria-controls="profile" aria-selected="false">Profile</a>
                        </li>
                        <li class="nav-item" role="presentation">
                            <a class="nav-link" id="contact-tab" data-toggle="tab" href="#contact" role="tab" aria-controls="contact" aria-selected="false">Contact</a>
                        </li>
                    </ul>
                    <div class="tab-content" id="myTabContent">
                        <div class="tab-pane fade show active" id="home" role="tabpanel" aria-labelledby="home-tab">1...</div>
                        <div class="tab-pane fade" id="profile" role="tabpanel" aria-labelledby="profile-tab">2...</div>
                        <div class="tab-pane fade" id="contact" role="tabpanel" aria-labelledby="contact-tab">3...</div>
                    </div>
                    <div id="content">
                        <ul id="tabs" class="nav nav-tabs">
                            <li role="presentation" class=nav-item">
                                <a class="nav-link active" href="#titledetails" data-toggle="tab">Titles/TIPPs
                                    <span class="badge badge-warning">${titleCount}</span>
                                </a>
                            </li>
                            <li role="presentation" class=nav-item">
                                <a class="nav-link" href="#identifiers" data-toggle="tab">Identifiers
                                    <span  class="badge badge-warning">${pkg?.getCombosByPropertyNameAndStatus('ids', 'Active')?.size() ?: '0'}</span>
                                </a>
                            </li>

                            <li role="presentation" class=nav-item">
                                <a class="nav-link" href="#altnames" data-toggle="tab">Alternate Names
                                    <span class="badge badge-warning">${pkg.variantNames?.size() ?: '0'}</span>
                                </a>
                            </li>
                        </ul>

                        <div id="my-tab-content" class="tab-content">

                            <div class="tab-pane active" id="titledetails">
                                <h2>Titles (${titleCount})</h2>
                                <table class="table table-striped table-bordered">
                                    <thead>
                                    <tr>
                                        <g:sortableColumn property="tipp.title.name" title="Title"/>
                                        <g:sortableColumn property="tipp.title.ids" title="Identifiers"/>
                                        <g:sortableColumn property="tipp.hostPlatform.name" title="Platform"/>
                                        <g:sortableColumn property="tipp.title.niceName" title="Title Type"/>
                                        <th>Coverage</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <g:each in="${tipps}" var="t">
                                        <tr>
                                            <td>
                                                <g:link controller="public" action="tippContent" id="${t.uuid}">
                                                    ${t.title.name}
                                                </g:link>
                                            </td>
                                            <td>
                                                <ul>
                                                    <g:each in="${t.title.ids}" var="id">
                                                        <li><strong>${id.namespace.value}</strong> : ${id.value}</li>
                                                    </g:each>
                                                </ul>
                                            </td>
                                            <td>
                                                <g:link controller="public" action="platformContent"
                                                        id="${t.hostPlatform?.uuid}">
                                                    ${t.hostPlatform?.name}
                                                </g:link>
                                            </td>
                                            <td>${t.title.niceName}</td>
                                            <td>
                                                ${t.coverageDepth?.value}<br/>${t.coverageNote}
                                            </td>
                                        </tr>
                                    </g:each>
                                    </tbody>
                                </table>

                                <div class="pagination" style="text-align:center">
                                    <g:if test="${titleCount ?: 0 > 0}">
                                        <g:paginate controller="public" action="packageContent" params="${params}"
                                                    next="Next"
                                                    prev="Prev" max="${max}" total="${titleCount}"/>
                                    </g:if>
                                </div>
                            </div>


                            <g:render template="/tabTemplates/showVariantnames" model="${[d: pkg]}"/>


                            <div class="tab-pane" id="identifiers">
                                <dl>
                                    <dt>
                                        <gokb:annotatedLabel owner="${pkg}"
                                                             property="ids">Identifiers</gokb:annotatedLabel>
                                    </dt>
                                    <dd>
                                        <g:render template="/apptemplates/secondTemplates/combosByType"
                                                  model="${[d: pkg, property: 'ids', fragment: 'identifiers', cols: [
                                                          [expr: 'toComponent.namespace.value', colhead: 'Namespace'],
                                                          [expr: 'toComponent.value', colhead: 'ID', action: 'link']]]}"/>
                                    </dd>
                                </dl>
                            </div>

                        </div>

                        <g:render template="/apptemplates/secondTemplates/componentStatus"
                                  model="${[d: pkg]}"/>

                    </div>

                </g:if>
        </div>
    </div>
    </div>

</div>
</body>
</html>
