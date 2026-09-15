/* Read each fetch response body exactly once and normalize Spring error responses. */
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
    let msg=`Request failed (${res.status})`;
    if(typeof data==='string'&&data.trim()) msg=data;
    else if(data&&typeof data==='object') {
      msg=data.detail||data.message||data.error||data.title||msg;
      if(typeof msg!=='string') {
        try{msg=JSON.stringify(msg);}catch{msg=`Request failed (${res.status})`;}
      }
    }
    throw new Error(msg);
  }
  return data;
};
