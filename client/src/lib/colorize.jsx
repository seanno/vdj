import { createElement } from 'react';

export function colorizeRearrangement(r) {

  const colorClasses = ['L','V','N1','D','N2','J','R'];

  // set up up array of segment indices, computing end of CDR3 by scanning for the
  // first called gene. All of this is a bit over-generalized but imnsho it makes
  // the code a lot more tolarable than a million if/thens
  
  const indices = [0, r.VIndex, r.N1Index, r.DIndex, r.N2Index, r.JIndex, -1, r.Rearrangement.length];
  
  for (var i = 1; i < 5; ++i) {
	if (indices[i] !== -1) {
	  indices[6] = indices[i] + r.Cdr3Length;
	  break;
	}
  }

  // console.log("BEFORE: " + JSON.stringify(indices));
  
  // fix up broken calls
  var runningMax = 0;
  for (var i = 0; i < indices.length; ++i) {
	if (indices[i] !== -1) {
	  if (indices[i] < runningMax) {
		indices[i] = -1; // can't be true
	  }
	  else {
		runningMax = indices[i];
	  }
	}
  }
  
  // console.log(" AFTER: " + JSON.stringify(indices));

  for (var i = 1; i < indices.length; ++i) {
	if (indices[i] < indices[i-1]) indices[i] = -1;
  }

  var jsx = [];

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



