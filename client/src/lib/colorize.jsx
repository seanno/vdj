import { createElement } from 'react';

export function colorizeRearrangement(r) {

  // set up our array of indices - "C" is for the start of CDR3 if called

  const colorClasses = ['V', 'C', 'V', 'N1', 'D', 'N2', 'J'];
  
  const indices = [0, r.VIndex, (r.VIndex === -1 ? -1 : r.VIndex + 3),
				   r.N1Index, r.DIndex, r.N2Index, r.JIndex, r.Rearrangement.length];
  
  // fix up out-of-order calls. in convo with Lik Wee & Lanny, it seems that our
  // J calls are "more trustable" that D/N1... so we do this correction right to left
  // which encapsulates that. May have to keep playing with this!

  var runningMin = indices[indices.length-1];
  for (var i = indices.length - 2; i >=0; i--) {
	if (indices[i] < -1) { indices[i] = -1; continue; } // wtf man
	if (indices[i] == -1) continue;
	if (indices[i] > runningMin) {
	  indices[i] = -1; // must be wrong
	}
	else {
	  runningMin = indices[i];
	}
  }

  const jsx = [];
  
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
	jsx.push(<span className={colorClasses[i]}>{seg}</span>);
  }

  // this dumb construct avoids react's key warning which is ignorable here
  return(createElement('div', {}, ...jsx));
}



