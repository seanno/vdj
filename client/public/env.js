
// API LOCATION

window.serverBase = 'https://spndev.mshome.net:3001/';

// DETAILS

window.detailsPageSize = 100;

// OVERLAP

window.overlapTypeDefault = 'CDR3';
window.overlapMaxSamples = 6;

// SEARCH

window.searchTypeDefault = 'Rearrangement';
window.searchFullDefault = false;
window.searchMutsDefault = 0;
window.searchJIndexDefault = -1;

window.searchTypeConfig = {
  
  'Rearrangement': {
	'minLength': 10,
	'maxMuts': 5,
	'unit': 'bases',
	'label': 'Nucleotide'
  },
  
  'AminoAcid': {
	'minLength': 5,
	'maxMuts': 2,
	'unit': 'acids',
	'label': 'Amino Acid'
  },
  
  'CDR3': {
	'minLength': 5,
	'maxMuts': 2,
	'unit': 'bases',
	'label': 'CDR3'
  },

  'MRD': {
	'minLength': 25,
	'maxMuts': 0,
	'unit': 'bases',
	'label': 'MRD'
  }
  
};

// TOPX

window.topXDefaultSort = 'FractionOfLocus';
window.topXCount = 100;

// AGATE

window.agateMinSearchLength = 5;

// EXPORT

window.exportDefaultFormat = 'Original';

// GENEUSE

window.geneUseDefaultIncludeUnknown = false;
window.geneUseDefaultIncludeFamilyOnly = false;
window.geneUseDefaultLog10Counts = false;

window.geneUseWidth = '600px';
window.geneUseHeight = '250px';

window.geneUseColors = [ '#4E79A7','#F28E2B','#E15759','#76B7B2','#59A14E',
						 '#EDC949','#B07AA2','#FF9DA7','#9C755F','#BAB0AC' ];

window.geneUseDx = 300;
window.geneUseDy = 100;
window.geneUseGap = .5;

