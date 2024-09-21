import { createElement } from 'react';

export function colorizeRearrangement(r) {

  const spans = getSpans(r);
  return(createElement('div', {}, ...spans));
}

function getSpans(r) {

  // n1/n2 is a mess. Pipeline puts n2 between V-D and n1 between D-J.
  // analyzer and agate "clean this up" and swap them. But we don't really
  // know what we're looking at (usually pipeline, but not always),
  // so we try to figure it out based on ordering in the file. eew.
  // END RESULT => V-N1-D-N2-J

  var n1 = r.N1Index;
  var n2 = r.N2Index;
  var swap = false;

  if (n1 !== -1 && n2 !== -1) {
	if (n1 > n2) swap = true;
  }
  else if (r.DIndex !== -1) {
	if ((n1 !== -1 && n1 > r.DIndex) ||
		(n2 !== -1 && n2 < r.DIndex)) {
	  swap = true;
	}
  }

  if (swap) {
	n1 = r.N2Index;
	n2 = r.N1Index;
  }

  // set up indices in order

  const indices = [ 0, n1, r.DIndex, n2, r.JIndex, r.Rearrangement.length ];

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



