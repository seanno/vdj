
import { memo, useState, useEffect } from 'react';
import { Button, FormControl, FormLabel, FormControlLabel,
		 ListItem, ListItemButton, ListItemIcon, Checkbox, ListItemText,
		 Radio, RadioGroup, Snackbar, TextField } from '@mui/material';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchSearch } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function SearchPane({ context, repertoires, params, rkey }) {

  const MRD_TYPE = 'MRD';
  const GENE_TYPE = 'Genes';

  const textDefault = (params && params.motif !== undefined  ? params.motif : '');
  const mutsDefault = (params && params.muts !== undefined ? params.muts : window.searchMutsDefault);
  const typeDefault = (params && params.type !== undefined ? params.type : window.searchTypeDefault);
  const fullDefault = (params && params.full !== undefined ? params.full : window.searchFullDefault);
  const jIndexDefault = (params && params.jIndex !== undefined ? params.jIndex : window.searchJIndexDefault);
  const startDefault = (params && params.start !== undefined ? params.start : false);
  
  const [searchText, setSearchText] = useState(textDefault);
  const [searchMuts, setSearchMuts] = useState(mutsDefault);
  const [searchType, setSearchType] = useState(typeDefault);
  const [searchFull, setSearchFull] = useState(fullDefault);
  const [searchJIndex, setSearchJIndex] = useState(jIndexDefault);
  const [startSearch, setStartSearch] = useState(startDefault);

  const [results, setResults] = useState(undefined);
  const [error,setError] = useState(undefined);

  function updateSearchType(newValue) {
	setSearchType(newValue);
	setSearchText(''); // if we're changing type whatever was there must be invalid
  }
  
  function toggleFullCheckbox() {
	setSearchFull(!searchFull);
  }

  function specialType(input) {
	return(input === MRD_TYPE || input === GENE_TYPE);
  }

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!startSearch || results) return;
	
	const loadResults = async () => {

	  serverFetchSearch(context, repertoires, searchText, searchType, searchMuts, searchFull, searchJIndex) 
		.then(result => {
		  setResults(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error searching repertoires');
		});
	}

	loadResults();
	
  }, [context, repertoires, searchText, searchType, searchMuts, searchFull, startSearch]);

  // +--------------+
  // | renderSearch |
  // +--------------+

  function renderSearch() {

	const lengthOK = ((searchText.length >= searchConfig.minLength) ||
					  (!specialType(searchType) && searchText.length > 0 && searchFull));
	
	const mutsOK = (specialType(searchType) || (searchMuts >= 0 && searchMuts <= searchConfig.maxMuts));

	const lengthHelper = ((!specialType(searchType) && searchFull)
						  ? 'sequence required'
						  : `at least ${searchConfig.minLength} ${searchConfig.unit} required`);

	const textLabel = ((searchType === GENE_TYPE)
					   ? 'Genes (CSV)'
					   :`${searchConfig.label} Sequence`);

	return(
	  <>
		<div className={styles.hdr}>
		  Searching in: { repertoires.map((r) => r.Name).join(', ') }
		</div>
	  
		<div className={styles.dialogTxt}>
		  <FormControl>
			<RadioGroup
			  row
			  value={searchType}
			  onChange={(evt) => updateSearchType(evt.target.value)} >
			  <FormControlLabel value='Rearrangement' control={<Radio/>} label='Nucleotide' />
			  <FormControlLabel value='CDR3' control={<Radio/>} label='CDR3' />
			  <FormControlLabel value='AminoAcid' control={<Radio/>} label ='Amino Acid' />
			  <FormControlLabel value='MRD' control={<Radio/>} label ='MRD' />
			  <FormControlLabel value='Genes' control={<Radio/>} label ='Genes' />
			</RadioGroup>
		  </FormControl>
		</div>

		<div className={styles.dialogTxt}>
		  <TextField
			autoFocus
			error={!lengthOK}
			label={textLabel}
			variant='outlined'
			value={searchText}
			sx={{ width: '100%' }}
			onChange={(evt) => setSearchText(evt.target.value)}
			helperText={lengthOK ? undefined : lengthHelper }
		  />
		</div>

		{ searchType === MRD_TYPE &&
			<div className={styles.dialogTxt}>
			  <TextField
				error={!mutsOK}
				label='J Index'
				variant='outlined'
				type='number'
				value={searchJIndex}
				onChange={(evt) => setSearchJIndex(evt.target.value)}
			  />
			</div>
		}

		{ !specialType(searchType) &&
		  <>
			<div className={styles.dialogTxt}>
			  <ListItem disablePadding>
				<ListItemButton
				  rule={undefined}
				  onClick={() => toggleFullCheckbox()}>
				  <ListItemIcon sx={{ minWidth: '20px' }}>
					<Checkbox
					  edge='start'
					  checked={searchFull}
					  tabIndex={-1}
					  disableRipple
					  inputProps={{ 'aria-labelledby': `${rkey}-full` }}
					  sx={{ padding: '0px' }}
					/>
				  </ListItemIcon>

				  <ListItemText
					sx={{ margin: '0px' }}
					id={`${rkey}-full`}
					primary='Match full sequence'
				  />
				</ListItemButton>
			  </ListItem>
			</div>

			<div className={styles.dialogTxt}>
			  <TextField
				error={!mutsOK}
				label='Allowed Mutations'
				variant='outlined'
				type='number'
				value={searchMuts}
				onChange={(evt) => setSearchMuts(evt.target.value)}
				helperText={mutsOK ? undefined : `0 to ${searchConfig.maxMuts} mutations allowed`}
			  />
			</div>
		  </>
		}

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
		  repertoire={result.Repertoire}
		  rearrangements={result.Rearrangements}
		  caption={result.Repertoire.Name}
		  key={`${rkey}-${irow}`}
		/>
	  );
	});

	return(
	  <>
		<div className={styles.hdr}>
		  { searchConfig.label }: { searchText }<br/>

		  { searchType === 'MRD' &&
			<>
			  J Index: {searchJIndex}
			</>
			
		  }

		  { !specialType(searchType) && 
			<>
			  { searchMuts } mutation{ searchMuts == 1 ? '' : 's'} allowed;
			  { searchFull ? ' full sequence match' : ' substring match' }
			</>
		  }
		  
		</div>
		{ tables }
	  </>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  const searchConfig = window.searchTypeConfig[searchType];
	
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
