<!DOCTYPE html>
<html>
<head>
    <meta name='layout' content='public'/>
    <title><g:message code="gokb.appname" default="we:kb"/>: Packages</title>
</head>

<body>

<g:render template="number-chart-hero"/>
<div class="container">
    <h1>Filter</h1>
    <div class="well well-lg wekb-filter">
        <g:form controller="public" class="form" role="form" action="index" method="get" params="${params}">
            <div class="row">
                <div class="col-sm-4">
                    <div class="form-group input-group-lg">
                        <label for="q">Search for packages...</label>
                        <input type="text" class="form-control" placeholder="Find package like..." value="${params.q}"
                                   name="q">
                     </div>
    %{--                    Showing results ${firstrec} to ${lastrec} of ${resultsTotal}--}%
                </div>

                    <g:each in="${facets?.sort { it.key }}" var="facet">
                        <g:if test="${facet.key != 'type'}">

                            <label for="${facet.key}" class="form-label"><g:message code="facet.so.${facet.key}"
                                                                                    default="${facet.key}"/></label>

                            <select name="${facet.key}" class="form-select wekb-multiselect" multiple
                                    aria-label="Default select example">
                                <option value="">Select <g:message code="facet.so.${facet.key}"
                                                                   default="${facet.key}"/></option>
                                <g:each in="${facet.value?.sort { it.display }}" var="v">
                                    <g:set var="fname" value="facet:${facet.key + ':' + v.term}"/>
                                    <g:set var="kbc"
                                           value="${v.term.startsWith('org.gokb.cred') ? org.gokb.cred.KBComponent.get(v.term.split(':')[1].toLong()) : null}"/>
                                    <g:if test="${params.list(facet.key).contains(v.term.toString())}">
                                        <option value="${v.term}"
                                                selected="selected">${kbc?.name ?: v.display} (${v.count})</option>
                                    </g:if>
                                    <g:else>
                                        <option value="${v.term}">${kbc?.name ?: v.display} (${v.count})</option>
                                    </g:else>
                                </g:each>
                            </select>

                        </g:if>
                    </g:each>


                    <br>
                    <br>

                    <button class="btn btn-primary" type="submit" value="yes" name="search"><span
                            class="fa fa-search" aria-hidden="true">Search</span></button>
                <g:each in="${facets?.sort { it.key }}" var="facet">
                    <div class="col-sm-4">
                        <div class="form-group input-group-lg">
                            <g:if test="${facet.key != 'type'}">
                                <label for="${facet.key}" class=""><g:message code="facet.so.${facet.key}" default="${facet.key}"/></label>
                                <select name="${facet.key}" class="form-control  wekb-multiselect" multiple aria-label="Default select example">
                                    <g:each in="${facet.value?.sort { it.display }}" var="v">
                                        <g:set var="fname" value="facet:${facet.key + ':' + v.term}"/>
                                        <g:set var="kbc"
                                               value="${v.term.startsWith('org.gokb.cred') ? org.gokb.cred.KBComponent.get(v.term.split(':')[1].toLong()) : null}"/>
                                        <g:if test="${params.list(facet.key).contains(v.term.toString())}">
                                            <option value="${v.term}"
                                                    selected="selected">${kbc?.name ?: v.display} (${v.count})</option>
                                        </g:if>
                                        <g:else>
                                            <option value="${v.term}">${kbc?.name ?: v.display} (${v.count})</option>
                                        </g:else>
                                    </g:each>
                                </select>
                            </g:if>
                        </div>
                    </div>
                </g:each>
            </div>
            <div class="row">
                <div class="col-sm-12">
                    <div class="form-group">
                        <a class="btn btn-default" style="margin-right: 20px" href="${grailsApplication.config.server.contextPath ?: ''}"/>Reset</a>
                        <button class="btn btn-primary" type="submit" value="yes" name="search">Search</button>
                    </div>
                </div>
            </div>
        </g:form>
    </div>
</div>


<div class="container">
    <div class="row">
        <div class="col-md-12">
            <h1>Results <span class="label label-default">${resultsTotal}</span></h1>
            <table class="table table-striped">
                <thead>
                <tr>
                    <g:sortableColumn property="sortname" title="Package Name"/>
                    <g:sortableColumn property="cpname" title="Provider"/>
                    <g:sortableColumn property="curatoryGroups" title="Curatory Groups"/>
                    <g:sortableColumn property="contentType" title="Content Type"/>
                    <g:sortableColumn property="titleCount" title="Title Count"/>
                    <g:sortableColumn property="lastUpdatedDisplay" title="Last Updated"/>
                </tr>
                </thead>
                <tbody>
                <g:each in="${hits}" var="hit">
                    <tr>
                        <td>
                            <g:link controller="public" action="packageContent"
                                    id="${hit.id}">${hit.source.name}</g:link>
                            <!-- <g:link controller="public" action="kbart"
                                         id="${hit.id}">(Download Kbart File)</g:link>-->

                        </td>
                        <td>${hit.source.cpname}</td>
                        <td>
                            <g:if test="${hit.source.curatoryGroups?.size() > 0}">
                                <g:each in="${hit.source.curatoryGroups}" var="cg" status="i">
                                    <g:if test="${i > 0}"><br></g:if>
                                    ${cg}
                                </g:each>
                            </g:if>
                            <g:else>
                                <div>No Curators</div>
                            </g:else>
                        </td>
                        <td>${hit.source.contentType}</td>
                        <td>${hit.source.titleCount}
                        %{--                        <g:if test="${hit.source.listStatus != 'Checked'}">*</g:if>--}%
                        </td>
                        <td>${hit.source.lastUpdatedDisplay}</td>
                    </tr>
                </g:each>
                </tbody>
            </table>

        %{--            <div style="font-size:0.8em;">
                        <b>*</b> The editing status of this package is marked as 'In Progress'. The number of titles in this package should therefore not be taken as final.
                    </div>--}%

            <g:if test="${resultsTotal ?: 0 > 0}">
                <div class="pagination">
                    <g:paginate controller="public" action="index" params="${params}" next="&raquo;" prev="&laquo;"
                                max="${max}" total="${resultsTotal}"/>
                </div>
            </g:if>

        </div>
    </div>

</div>

</div> <!-- /.container -->
<g:javascript>
    // When DOM is ready.
    $(document).ready(function () {

        var form_selects = $(".wekb-multiselect");

        form_selects.each(function () {

            var conf = {
                placeholder: "Please select",
                allowClear: true,
                width:'off',
                minimumInputLength: 0,
                /*                ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
                                    url: gokb.config.lookupURI,
                                    dataType: 'json',
                                    data: function (term, page) {
                                        return {
                                            format:'json',
                                            q: term,
                                            baseClass:$(this).data('domain'),
                                            filter1:$(this).data('filter1'),
                                            addEmpty:'Y'
                                        };
                                    },
                                    results: function (data, page) {
                                        // console.log("resultsFn");
                                        return {results: data.values};
                                    }
                                }*/
            };

            var me = $(this);


            me.select2(conf);
        });

    });
</g:javascript>
</body>
</html>
