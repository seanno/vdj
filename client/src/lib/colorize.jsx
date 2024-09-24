import { createElement } from 'react';

export function colorizeRearrangement(r) {

  const spans = getSpans(r);
  return(createElement('div', {}, ...spans));
}

function getSpans(r) {

  // set up indices in order

  const indices = [ 0, r.N1Index, r.DIndex, r.N2Index, r.JIndex, r.Rearrangement.length ];

  console.log(JSON.stringify(indices));
  
  const colorClasses = ['V', 'N1', 'D', 'N2', 'J'];

  // first look for broken calls --- other than -1 entires, values must grow
  // monotonically from left to right

  var max = (r.VIndex === -1 ? 0 : r.vIndex);
  for (var i = 1; i < indices.length; ++i) {
	if (indices[i] === -1) continue;
	if (indices[i] <= max) return([ <span className='V'>{r.Rearrangement}</span> ]);
	max = indices[i];
  }

  // OK at least the values conceivably make sense.
  // note weird handling of r.VIndex. We have it in the index

  const spans = [];
  
  for (var i = 0; i < colorClasses.length; ++i) {

	// only render this segment if start index exists
	if (indices[i] === -1) continue;

	// find the next called start index ... there will always be one because
	// the last entry is rearrangment length, not actually a called index
	var j = i + 1;
	while (j < indices.length) {
	  if (indices[j] !== -1) break;
	  ++j;
	}
	
	const seg = r.Rearrangement.substring(indices[i], indices[j]);
	spans.push(<span className={colorClasses[i]}>{seg}</span>);
  }

  return(spans);
}



