
// +------------------+
// | exportRepertoire |
// +------------------+

export function exportRepertoire(ctx, rep, fmt) {

  const url = window.serverBase + 'api/export' +
		'/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(rep) +
		'?fmt=' + fmt;

  window.location.href = url;
}

// +----------+
// | Wrappers |
// +----------+

// user
export async function serverFetchUser() {
  return(await serverFetch('/user'));
}

// contexts
export async function serverFetchContexts() {
  return(await serverFetch('/contexts/'));
}

// repertoires
export async function serverFetchRepertoires(ctx) {
  return(await serverFetch('/contexts/' + encodeURIComponent(ctx)));
}

// rearrangements
export async function serverFetchRepertoire(ctx, rep, start, count) {
  
  const url =
		'/contexts/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(rep) +
		'?start=' + start + '&count=' + count;

  return(await serverFetch(url));
}

// overlap
export async function serverFetchOverlap(ctx, reps, type, mode) {

  const url =
		'/overlap/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(',')) +
		'?type=' + encodeURIComponent(type) +
		(mode ? '&mode=' + encodeURIComponent(mode) : '');

  return(await serverFetch(url));
}

// search
export async function serverFetchSearch(ctx, reps, seq, type, muts, full) {

  const url =
		'/search/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(',')) +
		'?motif=' + encodeURIComponent(seq) +
		'&type=' + encodeURIComponent(type) +
		'&muts=' + muts.toString() +
		'&full=' + (full ? 'true' : 'false');

  return(await serverFetch(url));
}

// topx
export async function serverFetchTopX(ctx, rep, sort) {

  const url =
		'/topx/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(rep) +
		'?sort=' + encodeURIComponent(sort) +
		'&count=' + window.topXCount.toString();

  return(await serverFetch(url));
}

// upload
export async function serverFetchUpload(userId, context, repertoire, file, dateStr) {

  const dateParam = (dateStr ? 'date=' + new Date(dateStr).toISOString().substring(0, 10) : '');

  const url =
		'/contexts/' + encodeURIComponent(context) +
		'/' + encodeURIComponent(repertoire) +
		(userId ? '?user=' + encodeURIComponent(userId) : '') +
		(dateParam ? (userId ? '&' : '?') + dateParam : '');

  const nameLower = file.name.toLowerCase();
  const contentType = (nameLower.endsWith(".gz") ? "application/gzip" :
					   (nameLower.endsWith(".zip") ? "application/zip" :
						"application/octet-stream"))

  
  return(await serverFetch(url, file, contentType));
}

// delete
export async function serverFetchDelete(ctx, reps) {

  const url =
		'/contexts/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(',')) 

  return(await serverFetch(url, undefined,
						   'application/json', 'DELETE'));
}

// agate - samples
export async function serverFetchAgateSamples(user, pass, search) {

  const params = { "SearchString": search };
  if (user) params.User = user;
  if (pass) params.Password = pass;

  return(await serverFetch('/agate', JSON.stringify(params)));
}

// agate - import
export async function serverImportAgate(user, pass, ctx, saveUserId, sample) {

  const params = { "Sample": sample };
  if (user) params.User = user;
  if (pass) params.Password = pass;
  if (saveUserId) params.SaveUser = saveUserId;

  const url =
		'/agate/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(sample.Name);

  return(await serverFetch(url, JSON.stringify(params)));
}

// admin
export async function serverFetchAdmin(operation, body) {

  const url = '/admin/' + encodeURIComponent(operation);
  return(await serverFetch(url, body));
}

// tracking - dx options
export async function serverFetchDxOptions(ctx, reps) {

  const url =
		'/dxopt/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(','));

  return(await serverFetch(url));
}

// tracking - report

export async function serverFetchTracking(ctx, reps, rearrangements) {
  
  const body = JSON.stringify(rearrangements);

  const url =
		'/track/' + encodeURIComponent(ctx) +
		'/' + encodeURIComponent(reps.map((r) => r.Name).join(','));

  return(await serverFetch(url, body));
}

// +-------------+
// | serverFetch |
// +-------------+

export async function serverFetch(relativeUrl, body, contentType = 'application/json', method = undefined) {

  const url = window.serverBase + 'api' + relativeUrl;
    // don't think we need this but will save just in case
	// + (relativeUrl.indexOf("?") === -1 ? "?" : "&" ) +
	// 	"rand=" + Math.random();

  const options = {
	method: (method ? method : (body ? 'POST' : 'GET')),
	headers: { }
  }

  if (body) {
	options.headers['Content-Type'] = contentType;
	options.body = body;
  }

  console.log(`${options.method} ${url}`);
  const response = await fetch(url, options);

  if (response.status === 409) {
	return({ httpStatus: 409 });
  }
  
  if (response.status !== 200) {
	throw new Error(`serverFetch ${url}: ${response.status}`);
  }

  return(await response.json());
}
