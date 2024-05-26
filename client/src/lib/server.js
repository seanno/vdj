
// +----------+
// | Wrappers |
// +----------+

export async function serverFetchUser() {
  return(await serverFetch('/user'));
}

export async function serverFetchContexts() {
  return(await serverFetch('/contexts/'));
}

export async function serverFetchRepertoires(ctx) {
  return(await serverFetch('/contexts/' + encodeURIComponent(ctx)));
}

export async function serverFetchRepertoire(ctx, rep, start, count) {
  
  const url =
		'/contexts/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(rep) +
		'?start=' + start + '&count=' + count;

  return(await serverFetch(url));
}

export async function serverFetchSearch(ctx, reps, isAA, seq, muts) {

  const url =
		'/search/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(',')) +
		'?isaa=' + (isAA ? 'true' : 'false') +
		'&motif=' + encodeURIComponent(seq) +
		'&muts=' + muts.toString();

  return(await serverFetch(url));
}

export async function serverFetchUpload(userId, context, repertoire, file) {

  const url =
		'/contexts/' + encodeURIComponent(context) +
		'/' + encodeURIComponent(repertoire) +
		(userId ? '&user=' + encodeURIComponent(userId) : '');

  const nameLower = file.name.toLowerCase();
  const contentType = (nameLower.endsWith(".gz") ? "application/gzip" :
					   (nameLower.endsWith(".zip") ? "application/zip" :
						"application/octet-stream"))

  
  return(await serverFetch(url, file, contentType));
}
// +-------------+
// | serverFetch |
// +-------------+

export async function serverFetch(relativeUrl, body, contentType = 'application/json') {

  const url = window.serverBase + 'api' + relativeUrl;
    // don't think we need this but will save just in case
	// + (relativeUrl.indexOf("?") === -1 ? "?" : "&" ) +
	// 	"rand=" + Math.random();

  const options = {
	method: (body ? 'POST' : 'GET'),
	headers: { }
  }

  if (body) {
	options.headers['Content-Type'] = contentType;
	options.body = body;
  }

  console.log(`${options.method} ${url}`);
  const response = await fetch(url, options);

  if (response.status !== 200) {
	throw new Error(`serverFetch ${url}: ${response.status}`);
  }

  return(await response.json());
}
