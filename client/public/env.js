
// API LOCATION

window.serverBase = 'https://spndev.mshome.net:3001/';

// DETAILS

window.detailsPageSize = 100;

// OVERLAP

window.overlapTypeDefault = 'CDR3';

// SEARCH

window.searchTypeDefault = 'Rearrangement';
window.searchFullDefault = false;
window.searchMutsDefault = 0;

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
  }
};

// TOPX

window.topXDefaultSort = 'FractionOfLocus';
window.topXCount = 100;

// AGATE

window.agateMinSearchLength = 5;

