<dl class="dl-horizontal">
  <dt> <gokb:annotatedLabel owner="${d}" property="value">Value</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditable class="ipe" owner="${d}" field="value" /> </dd>

  <dt> <gokb:annotatedLabel owner="${d}" property="name">Name</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditable class="ipe" owner="${d}" field="name" /> </dd>

  <dt> <gokb:annotatedLabel owner="${d}" property="datatype">RDF Datatype</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditableRefData owner="${d}" field="datatype" config='RDFDataType' /> </dd>

  <dt> <gokb:annotatedLabel owner="${d}" property="family">Category</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditable class="ipe" owner="${d}" field="family" /> </dd>

  <dt> <gokb:annotatedLabel owner="${d}" property="pattern">Pattern</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditable class="ipe" owner="${d}" field="pattern" /> </dd>

  <dt> <gokb:annotatedLabel owner="${d}" property="targetType">Target Type</gokb:annotatedLabel> </dt>
  <dd> <gokb:xEditableRefData owner="${d}" field="targetType" config='IdentifierNamespace.TargetType' /> </dd>
</dl>
