
import { useState, useEffect } from 'react';
import { serverFetchContexts } from './lib/server.js';

export default function ContextPicker() {

  const [contextList,setContextList] = useState(undefined);
  const [error,setError] = useState(undefined);

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	const loadContexts = async () => {
	  serverFetchContexts()
		.then(result => {
		  console.log('yo');
		  setContextList(result);
		})
		.catch(error => {
		  console.error(error);
		  setError(error);
		});
	}

	loadContexts();
	
  }, []);

  // +--------+
  // | Render |
  // +--------+

  if (!contextList) return(undefined);
  
  return(
	
    <div>
	  <pre><code>{JSON.stringify(contextList, null, 2)}</code></pre>
	</div>
	
  );
}

