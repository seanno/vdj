
import { useState, useEffect } from 'react';
import { Autocomplete, TextField } from '@mui/material';
import { serverFetchContexts } from './lib/server.js';

export default function ContextPicker({ onContextChange, showError }) {

  const [contextList,setContextList] = useState(undefined);

  // +-----------+
  // | useEffect |
  // +-----------+

  // initial load of user contexts
  
  useEffect(() => {

	const loadContexts = async () => {
	  serverFetchContexts()
		.then(result => {
		  setContextList(result);
		})
		.catch(error => {
		  console.error(error);
		  showError('Error loading user contexts');
		});
	}

	loadContexts();
	
  }, []);

  // +--------+
  // | Render |
  // +--------+

  return(

	
    <div>

	  { !contextList &&
		<p>Loading Contexts...</p>
	  }
	  
	  { contextList &&
		<Autocomplete
		  disablePortal
		  sx={{ width: '100%' }}
		  options={ contextList }
		  onChange={ (evt, newValue) => onContextChange(newValue) }
		  renderInput={ (params) => <TextField {...params} label="Context" /> }
		/>
	  }

	</div>
	
  );
}

