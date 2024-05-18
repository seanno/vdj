
// +----------+
// | Wrappers |
// +----------+

export async function serverFetchContexts() {
  return(await serverFetch(''));
}

// +-------------+
// | serverFetch |
// +-------------+

export async function serverFetch(relativeUrl, body) {

  const url = window.serverBase + 'api/contexts' + relativeUrl;
  
  const options = {
	method: (body ? 'POST' : 'GET'),
	headers: { }
  }

  if (body) {
	options.headers['Content-Type'] = 'application/json';
	options.body = body;
  }
  
  const response = await fetch(url, options);

  if (response.status !== 200) {
	throw new Error(`serverFetch ${url}: ${response.status}`);
  }

  return(await response.json());
}
