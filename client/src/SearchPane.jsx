
import { memo, useState, useEffect } from 'react';
import { Button, FormControl, FormLabel, FormControlLabel,
		 Radio, RadioGroup, Snackbar, TextField } from '@mui/material';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchSearch } from './lib/server.js';

import styles from './Search.module.css'

export default memo(function SearchPane({ context, repertoires, rkey }) {

  const [searchText, setSearchText] = useState('');
  const [searchMuts, setSearchMuts] = useState(0);
  const [searchIsAA, setSearchIsAA] = useState(false);
  const [startSearch, setStartSearch] = useState(false);

  const [results, setResults] = useState(undefined);
  const [error,setError] = useState(undefined);

  function updateSearchType(newValue) {
	setSearchIsAA(newValue === 'true'); // RadioGroup value is always text, seems weird but ok
	setSearchText(''); // if we're changing type whatever was there must be invalid
  }
  
  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!startSearch || results) return;
	
	const loadResults = async () => {

	  serverFetchSearch(context, repertoires, searchIsAA, searchText, searchMuts) 
		.then(result => {
		  setResults(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error searching repertoires');
		});
	}

	loadResults();
	
  }, [context, repertoires, searchText, searchMuts, startSearch]);



  // +--------------+
  // | renderSearch |
  // +--------------+

  function renderSearch() {

	const [seqMin, mutsMax, seqType, seqLabel] = searchIsAA
		  ? [ window.searchAASeqMin, window.searchAAMutsMax, 'acids', 'Amino Acid Sequence' ]
		  : [ window.searchNucSeqMin, window.searchNucMutsMax, 'bases', 'Nucleotide Sequence' ];

	const lengthOK = (searchText.length >= seqMin);
	const mutsOK = (searchMuts >= 0 && searchMuts <= mutsMax);
	
	return(
	  <>
		<div className={styles.hdr}>
		  Searching in: { repertoires.map((r) => r.Name).join(', ') }
		</div>
	  
		<div className={styles.dialogTxt}>
		  <FormControl>
			<RadioGroup
			  row
			  value={searchIsAA}
			  onChange={(evt) => updateSearchType(evt.target.value)} >
			  <FormControlLabel value={false} control={<Radio/>} label='Nucleotide' />
			  <FormControlLabel value={true} control={<Radio/>} label ='Amino Acid' />
			</RadioGroup>
		  </FormControl>
		</div>

		<div className={styles.dialogTxt}>
		  <TextField
			autoFocus
			error={!lengthOK}
			label={seqLabel}
			variant='outlined'
			value={searchText}
			sx={{ width: '100%' }}
			onChange={(evt) => setSearchText(evt.target.value)}
			helperText={lengthOK ? undefined : `at least ${seqMin} ${seqType} required`}
		  />
		</div>

		<div className={styles.dialogTxt}>
		  <TextField
			error={!mutsOK}
			label='Allowed Mutations'
			variant='outlined'
			type='number'
			value={searchMuts}
			onChange={(evt) => setSearchMuts(evt.target.value)}
			helperText={mutsOK ? undefined : `0 to ${mutsMax} mutations allowed`}
		  />
		  </div>

		<Button
		  variant='outlined'
		  disabled={(!lengthOK || !mutsOK)}
		  onClick={() => setStartSearch(true)} >
		  Go
		</Button>

	  </>
	);
  }

  // +---------------+
  // | renderLoading |
  // +---------------+

  function renderLoading() {
	return(<div>Searching...</div>);
  }

  // +---------------+
  // | renderResults |
  // +---------------+

  function renderResults() {

	const tables = results.map((result, irow) => {
	  return(
		<RearrangementsTable
		  rearrangements={result.Rearrangements}
		  caption={result.Repertoire.Name}
		  key={`${rkey}-${irow}`}
		/>
	  );
	});

	return(
	  <>
		<div className={styles.hdr}>
		  { searchIsAA ? 'Amino Acid' : 'Nucleotide' } { searchText }<br/>
		  {searchMuts} mutation{ searchMuts == 1 ? '' : 's'} allowed
		</div>
		{ tables }
	  </>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { !results && !startSearch && renderSearch() }
	  { !results && startSearch && renderLoading() }
	  { results && renderResults() }
	  
	  <Snackbar
		open={error !== undefined}
		autoHideDuration={ 2500 }
		message={error}
		anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
		onClose={ () => setError(undefined) }
	  />
	</div>
	
  );
}

)
