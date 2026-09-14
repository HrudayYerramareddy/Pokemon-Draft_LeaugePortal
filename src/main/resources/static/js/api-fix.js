/* Read each fetch response body exactly once. This avoids "Response already read" when an endpoint returns plain text or an empty non-204 body. */
api=async function(url,options={}){
  const opts={headers:{'Content-Type':'application/json'},...options};
  if(options.body&&typeof options.body!=='string')opts.body=JSON.stringify(options.body);
  const res=await fetch(url,opts);
  if(res.status===204)return null;

  const raw=await res.text();
  let data=null;
  if(raw){
    try{data=JSON.parse(raw);}catch{data=raw;}
  }

  if(!res.ok){
    const msg=(data&&typeof data==='object'&&(data.detail||data.message))||data||`Request failed (${res.status})`;
    throw new Error(msg);
  }
  return data;
};
