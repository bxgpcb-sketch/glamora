
/* ---------- account details ---------- */
function openAccount(){
  const m=$("#modal"),me=S.profile||{};
  m.innerHTML=`<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="aTitle">
    <div class="sheet-head"><h2 id="aTitle">Detalii cont</h2><button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button></div>
    <div class="sheet-body">
      <div class="kv"><span>Email</span><b>${esc(S.user?.email||"")}</b></div>
      <div class="kv"><span>Nume</span><b>${esc(me.staff?staffName(me.staff):(me.name||"—"))}</b></div>
      <div class="kv"><span>Rol</span><b>${esc(me.staff?(STAFF.find(s=>s.id===me.staff)||{}).role:"Administrator")}</b></div>
      <button type="button" class="btn" id="aWho" style="width:100%;margin-top:6px">Schimbă cine sunt</button>
      <h3 class="sec">Schimbă parola</h3>
      <form id="pwForm" class="cform" style="margin-top:8px" autocomplete="on">
        <input type="email" autocomplete="username" value="${esc(S.user?.email||"")}" hidden>
        <div class="field"><label for="pwOld">Parola actuală</label><input id="pwOld" type="password" autocomplete="current-password" required></div>
        <div class="field"><label for="pwNew">Parola nouă (minim 6 caractere)</label><input id="pwNew" type="password" autocomplete="new-password" minlength="6" required></div>
        <div class="warn" id="pwErr" hidden></div>
        <button class="btn primary" id="pwBtn" type="submit">Salvează parola nouă</button>
      </form>
    </div></div>`;
  m.hidden=false;lockScroll(true);
  const close=()=>{m.hidden=true;m.innerHTML="";lockScroll(false)};
  $("#xBtn").onclick=close;
  $("#aWho").onclick=()=>{close();pickProfile(true)};
  $("#pwForm").onsubmit=async e=>{e.preventDefault();const err=$("#pwErr");err.hidden=true;$("#pwBtn").disabled=true;
    try{await authApi.changePassword($("#pwOld").value,$("#pwNew").value);close();toast("Parola a fost schimbată")}
    catch(x){err.hidden=false;err.textContent=authMsg(x);$("#pwBtn").disabled=false}};
}

/* ---------- which colleague is this account ---------- */
function pickProfile(force){return new Promise(res=>{
  const m=$("#modal");
  m.innerHTML=`<div class="sheet fit" role="dialog" aria-modal="true" aria-labelledby="pTitle">
    <div class="sheet-head"><h2 id="pTitle">Cine folosește contul?</h2>${force?'<button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button>':""}</div>
    <div class="sheet-body"><p class="hint" style="margin:0 0 12px;font-size:14px">Se alege o singură dată. Programările noi vor fi puse direct în coloana ta.</p>
      <div style="display:grid;gap:8px">${STAFF.map(s=>`<button type="button" class="btn who-pick" data-id="${s.id}" style="border-left:5px solid ${s.color}"><b>${s.name}</b><span>${s.role}</span></button>`).join("")}
      <button type="button" class="btn who-pick" data-id=""><b>Administrator</b><span>Vede și editează toate coloanele</span></button></div></div></div>`;
  m.hidden=false;lockScroll(true);
  const done=()=>{m.hidden=true;m.innerHTML="";lockScroll(false);res()};
  if(force)$("#xBtn").onclick=done;
  m.querySelectorAll(".who-pick").forEach(b=>b.onclick=async()=>{
    const p={staff:b.dataset.id||null,name:b.dataset.id?staffName(b.dataset.id):"Administrator",email:S.user.email,updatedAt:new Date().toISOString()};
    try{await db.doc("users/"+S.user.uid).set(p)}catch(e){}
    S.profile=p;done();toast("Bun venit, "+p.name+"!")});
})}

/* ---------- login ---------- */
function authMsg(e){const c=e&&e.code||"";
  if(/invalid-credential|wrong-password|user-not-found|invalid-login/.test(c))return "Email sau parolă greșită.";
  if(/invalid-email/.test(c))return "Adresa de email nu e scrisă corect.";
  if(/too-many-requests/.test(c))return "Prea multe încercări. Mai încearcă peste câteva minute.";
  if(/network-request-failed/.test(c))return "Nu ai conexiune la internet.";
  if(/weak-password/.test(c))return "Parola nouă trebuie să aibă minim 6 caractere.";
  if(/user-disabled/.test(c))return "Contul este dezactivat.";
  return "Nu a mers ("+c.replace("auth/","")+").";}
function showLogin(){
  $("#appRoot").hidden=true;const L=$("#login");L.hidden=false;
  $("#lErr").hidden=true;$("#lBtn").disabled=false;$("#lBtn").textContent="Intră în cont";
}
$("#lForm").onsubmit=async e=>{e.preventDefault();const err=$("#lErr");err.hidden=true;
  $("#lBtn").disabled=true;$("#lBtn").textContent="Se conectează…";
  try{await authApi.signIn($("#lEmail").value.trim(),$("#lPass").value)}
  catch(x){err.hidden=false;err.textContent=authMsg(x);$("#lBtn").disabled=false;$("#lBtn").textContent="Intră în cont"}};
$("#lShow").onclick=()=>{const p=$("#lPass");p.type=p.type==="password"?"text":"password";$("#lShow").textContent=p.type==="password"?"Arată":"Ascunde"};
$("#lForgot").onclick=async()=>{const em=$("#lEmail").value.trim(),err=$("#lErr");
  if(!em){err.hidden=false;err.textContent="Scrie emailul tău mai sus, apoi apasă din nou.";return}
  try{await authApi.resetPassword(em);err.hidden=false;err.textContent="Ți-am trimis pe email un link pentru parolă nouă."}
  catch(x){err.hidden=false;err.textContent=authMsg(x)}};

/* ---------- data ---------- */
const subs=[];
function stopData(){while(subs.length)subs.pop()();if(S.unsubAppts){S.unsubAppts();S.unsubAppts=null}S.db=null;S.appts=[];S.clients=[]}
let seeded=false;
function startData(){
  S.db=db;
  subs.push(db.doc("config/salon").onSnapshot(s=>{
    if(!s.exists){if(!s.fromCache&&!seeded){seeded=true;db.doc("config/salon").set(DEFAULTS).catch(()=>{})}return}
    const d=s.data();S.services=Array.isArray(d.services)?d.services:[];if(d.hours)S.hours=d.hours;renderGrid()},e=>toast("Eroare la încărcare ("+e.code+")")));
  subs.push(db.collection("clients").onSnapshot(s=>{S.clients=s.docs.map(d=>({id:d.id,...d.data()}))},()=>{}));
  subscribeDay();
}

/* ---------- offline notice ---------- */
const net=()=>{$("#dbNotice").hidden=navigator.onLine};
addEventListener("online",net);addEventListener("offline",net);net();

/* ---------- boot ---------- */
S.services=DEFAULTS.services;S.hours=DEFAULTS.hours;
renderGrid();
setInterval(()=>{if(S.date===todayStr()&&$("#modal").hidden&&!$("#appRoot").hidden)renderGrid()},60000);
authApi.onChange(async user=>{
  $("#splash").hidden=true;
  if(!user){stopData();S.user=null;S.profile=null;showLogin();return}
  S.user=user;$("#login").hidden=true;$("#appRoot").hidden=false;$("#lPass").value="";
  try{const p=await db.doc("users/"+user.uid).get();S.profile=p.exists?p.data():null}catch(e){S.profile=null}
  startData();
  if(!S.profile)await pickProfile(false);
});
if("serviceWorker" in navigator)addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
