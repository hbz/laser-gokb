<g:each var="entry" in="${rd.sort{it.value.title}}">
	<g:if test="${ entry.key.startsWith(dtype + '.' ) && !(entry.key in notShowProps)}">
		<dt>
			<gokb:annotatedLabel owner="${d}" property="${ entry.value.title }">
				${ entry.value.title }
			</gokb:annotatedLabel>
		</dt>
		<dd>
			<gokb:xEditableRefData owner="${d}" field="${entry.value.name}"
				config="${entry.key}" />
		</dd>
	</g:if>
</g:each>
