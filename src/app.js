import { db, authApi } from "./fb.js";
import DEFAULTS from "./default-config.json";

const STAFF=[{id:"tina",name:"Tina G",role:"Coafor",color:"var(--tina)"},{id:"mymy",name:"Mymy C",role:"Cosmetică",color:"var(--mymy)"},{id:"irina",name:"Irina B",role:"Manichiură",color:"var(--irina)"}];
const DURS=[15,30,45,60,75,90,105,120,150,180,210,240];
const STEP=15;
const ICON={
  call:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/></svg>',
  sms:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 5h16v11H9l-5 4z"/></svg>',
  wa:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20l1.3-4A8 8 0 1 1 8 18.7z"/><path d="M9 9c0 3 3 6 6 6l1-1.5-2-1-1 1c-1-.4-2-1.4-2.5-2.5l1-1-1-2z"/></svg>',
  copy:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/></svg>',
  cal:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
  today:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  out:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11"/></svg>',
  user:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/></svg>'};
const S={db:null,date:todayStr(),appts:[],clients:[],services:[],hours:{start:"09:00",end:"20:00"},unsubAppts:null,needScroll:true};
const $=s=>document.querySelector(s);
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
function pad(n){return String(n).padStart(2,"0")}
function ymd(d){return d.getFullYear()+"-"+pad(d.getMonth()+1)+"-"+pad(d.getDate())}
function todayStr(){return ymd(new Date())}
function toMin(t){const[h,m]=(t||"0:0").split(":").map(Number);return h*60+m}
function toTime(m){return pad(Math.floor(m/60))+":"+pad(m%60)}
function durLabel(m){const h=Math.floor(m/60),r=m%60;return h?(h+" h"+(r?" "+r:"")):m+" min"}
function shiftDate(s,n){const d=new Date(s+"T12:00:00");d.setDate(d.getDate()+n);return ymd(d)}
function fmtDate(s,opt){return new Date(s+"T12:00:00").toLocaleDateString("ro-RO",opt||{weekday:"long",day:"numeric",month:"long"})}
function relDay(s){const t=todayStr();return s===t?"Azi":s===shiftDate(t,1)?"Mâine":s===shiftDate(t,-1)?"Ieri":fmtDate(s,{year:"numeric"})}
function digits(p){return String(p||"").replace(/\D/g,"")}
function ppm(){return parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--ppm"))||2.4}
function toast(msg){const t=$("#toast");t.textContent=msg;t.hidden=false;clearTimeout(toast._t);toast._t=setTimeout(()=>t.hidden=true,2200)}
function staffName(id){return (STAFF.find(s=>s.id===id)||{}).name||id}
/* "Maria Pop 0744 123 456" -> {name:"Maria Pop", phone:"0744 123 456"} */
function splitClient(v){v=String(v||"").trim();const m=v.match(/^(.*?)[\s,;-]*((?:\+?\d[\d\s.\-]{5,}\d))$/);
  return m?{name:m[1].trim(),phone:m[2].trim()}:{name:v,phone:""}}
function isBlock(name){const n=String(name||"").trim().toLowerCase();return S.services.some(s=>s.block&&s.name.toLowerCase()===n)||n==="pauză"||n==="pauza"||n==="ocupat"}
function clientLine(a){return a.phone?`${a.client} ${a.phone}`:(a.client||"")}
function intl(phone){let d=digits(phone);if(String(phone).trim().startsWith("+"))return d;if(d.startsWith("00"))return d.slice(2);if(d.startsWith("0"))return "40"+d.slice(1);return d}
const STATUSES=[["acceptata","Acceptată"],["confirmata","Confirmată"],["anulata","Anulată"]];
function statusLabel(k){return (STATUSES.find(x=>x[0]===k)||STATUSES[0])[1]}
function detailed(a){return `Bună ziua${a.client?", "+a.client:""}! Vă reamintim programarea la Salon Vintage ${fmtDate(a.date)}, ora ${a.start}, la ${staffName(a.staff)}${a.service?" ("+a.service+")":""}. Răspundeți cu 1 pentru confirmare sau 0 pentru anulare. Mulțumim!`}
function reminder(a){return `Bună ziua${a.client?", "+a.client:""}! Vă reamintim programarea la Salon Vintage ${fmtDate(a.date)}, ora ${a.start}, la ${staffName(a.staff)}${a.service?" ("+a.service+")":""}. Vă așteptăm!`}
function durOptions(sel){const list=DURS.includes(Number(sel))?DURS:[...DURS,Number(sel)].sort((a,b)=>a-b);return list.map(d=>`<option value="${d}" ${d===Number(sel)?"selected":""}>${durLabel(d)}</option>`).join("")}
async function copyText(t){try{await navigator.clipboard.writeText(t);toast("Text copiat")}catch(e){toast("Nu s-a putut copia")}}
function lockScroll(on){document.body.style.overflow=on?"hidden":""}

/* ---------- calendar picker ---------- */
function openCalendar(current,onPick){
  const m=$("#calModal");let view=new Date(current+"T12:00:00");view.setDate(1);
  const draw=()=>{const y=view.getFullYear(),mo=view.getMonth();
    const first=(new Date(y,mo,1).getDay()+6)%7,days=new Date(y,mo+1,0).getDate(),t=todayStr();
    let cells=["L","Ma","Mi","J","V","S","D"].map(d=>`<div class="wd">${d}</div>`).join("");
    for(let i=0;i<first;i++)cells+="<div></div>";
    for(let d=1;d<=days;d++){const s=y+"-"+pad(mo+1)+"-"+pad(d),dow=(first+d-1)%7;
      cells+=`<button type="button" data-d="${s}" class="${s===current?"sel":""} ${s===t?"today":""} ${dow===6?"sun":""}">${d}</button>`}
    m.innerHTML=`<div class="sheet fit" role="dialog" aria-modal="true" aria-label="Calendar"><div class="cal">
      <div class="cal-head">
        <button type="button" class="btn icon" data-nav="-12" aria-label="Anul anterior">«</button>
        <button type="button" class="btn icon" data-nav="-1" aria-label="Luna anterioară">‹</button>
        <div class="m">${view.toLocaleDateString("ro-RO",{month:"long",year:"numeric"})}</div>
        <button type="button" class="btn icon" data-nav="1" aria-label="Luna următoare">›</button>
        <button type="button" class="btn icon" data-nav="12" aria-label="Anul următor">»</button>
      </div>
      <div class="cal-grid">${cells}</div>
      <div class="cal-foot"><button type="button" class="btn" id="calClose">Închide</button><button type="button" class="btn primary" id="calToday">Azi</button></div>
    </div></div>`;
    m.querySelectorAll("[data-nav]").forEach(b=>b.onclick=()=>{view.setMonth(view.getMonth()+Number(b.dataset.nav));draw()});
    m.querySelectorAll("[data-d]").forEach(b=>b.onclick=()=>{close();onPick(b.dataset.d)});
    $("#calClose").onclick=close;$("#calToday").onclick=()=>{close();onPick(todayStr())}};
  const close=()=>{m.hidden=true;m.innerHTML="";if($("#modal").hidden)lockScroll(false)};
  m.onclick=e=>{if(e.target===m)close()};
  draw();m.hidden=false;lockScroll(true);
}

/* ---------- agenda ---------- */
function renderGrid(){
  const start=toMin(S.hours.start),end=toMin(S.hours.end),k=ppm(),H=(end-start)*k;
  let html="";
  for(const st of STAFF)html+=`<div class="colhead" style="border-bottom-color:${st.color}"><span class="nm">${esc(st.name)}</span><span class="rl">${esc(st.role)}</span></div>`;
  const d=new Date(),nowM=d.getHours()*60+d.getMinutes();
  for(const st of STAFF){let c=`<div class="col" style="height:${H}px" data-staff="${st.id}">`;
    for(let m=start;m<end;m+=STEP)c+=`<button class="slot${m%60===0?" hour":""}" style="top:${(m-start)*k}px;height:${STEP*k}px;${m===start?"border-top:0":""}" data-t="${toTime(m)}" aria-label="Programare nouă la ${st.name}, ${toTime(m)}">${toTime(m)}</button>`;
    if(S.date===todayStr()&&nowM>=start&&nowM<=end)c+=`<div class="now" style="top:${(nowM-start)*k}px"></div>`;
    for(const a of S.appts.filter(a=>a.staff===st.id)){const top=(toMin(a.start)-start)*k+1,h=Math.max(STEP*k-2,(a.dur||30)*k-2);
      const blk=isBlock(a.service);
      const st2=blk?"":(a.status&&a.status!=="acceptata"?" st-"+a.status:"");
      c+=`<button class="appt${blk?" block":""}${st2}" data-id="${esc(a.id)}" style="top:${top}px;height:${h}px;border-left-color:${st.color}">
        <span class="t">${esc(a.start)} · ${durLabel(a.dur||30)}${!blk&&a.status==="confirmata"?" · ✓ Confirmată":""}${!blk&&a.status==="anulata"?" · ✕ Anulată":""}${!blk&&a.status!=="confirmata"&&a.status!=="anulata"&&a.confirmRequestedAt?" · ⏳":""}</span><span class="c">${esc(blk?(a.service||"Ocupat"):(a.client||"Fără nume"))}</span>${blk?(a.client?`<span class="s">${esc(a.client)}</span>`:""):(a.service?`<span class="s">${esc(a.service)}</span>`:"")}</button>`}
    html+=c+`</div>`}
  const g=$("#grid");g.innerHTML=html;
  g.querySelectorAll(".slot").forEach(b=>b.onclick=()=>openAppt(null,{staff:b.parentElement.dataset.staff,start:b.dataset.t}));
  g.querySelectorAll(".appt").forEach(b=>b.onclick=()=>openAppt(S.appts.find(a=>a.id===b.dataset.id)));
  $("#dateLabel").textContent=fmtDate(S.date);
  $("#dateSub").textContent=relDay(S.date)+" · "+S.appts.length+(S.appts.length===1?" programare":" programări");
  if(S.needScroll){S.needScroll=false;const sc=$("#scroller");
    sc.scrollTop=S.date===todayStr()&&nowM>start&&nowM<end?Math.max(0,(nowM-start-30)*k):0}
}
function subscribeDay(){
  if(S.unsubAppts){S.unsubAppts();S.unsubAppts=null}
  S.appts=[];S.needScroll=true;renderGrid();if(!S.db)return;
  S.unsubAppts=S.db.collection("appointments").where("date","==",S.date).onSnapshot(snap=>{
    S.appts=snap.docs.map(d=>({id:d.id,...d.data()}));renderGrid()},e=>toast("Agenda nu s-a încărcat ("+e.code+")"));
}
function goDate(d){S.date=d;subscribeDay()}
$("#prevDay").onclick=()=>goDate(shiftDate(S.date,-1));
$("#nextDay").onclick=()=>goDate(shiftDate(S.date,1));
$("#dateBtn").onclick=()=>openCalendar(S.date,goDate);
$("#newBtn").onclick=()=>{const d=new Date(),nm=Math.ceil((d.getHours()*60+d.getMinutes())/STEP)*STEP,s=toMin(S.hours.start),e=toMin(S.hours.end);
  openAppt(null,{staff:S.profile?.staff||"tina",start:toTime(S.date===todayStr()&&nm>s&&nm<e?nm:s)})};

/* ---------- menu ---------- */
$("#menuBtn").onclick=()=>{const dr=$("#drawer");
  const me=S.profile||{};
  dr.innerHTML=`<nav class="drawer" aria-label="Meniu"><h2>Glamora</h2><p class="sub">Salon Vintage · Piatra Neamț</p>
    <div class="who"><b>${esc(me.staff?staffName(me.staff):(me.name||"Cont"))}</b><span>${esc(S.user?.email||"")}</span></div>
    <button class="item" id="mToday">${ICON.today}Mergi la azi</button>
    <button class="item" id="mCal">${ICON.cal}Alege data</button>
    <button class="item" id="mRem">${ICON.sms}Reamintiri pentru mâine</button>
    <button class="item" id="mClients">${ICON.user}Caută clientă</button>
    <button class="item" id="mAcc">${ICON.user}Detalii cont</button>
    <span style="flex:1"></span>
    <button class="item danger-item" id="mOut">${ICON.out}Deconectare</button>
    <button class="btn" id="mClose">Închide</button></nav>`;
  dr.hidden=false;
  const close=()=>{dr.hidden=true;dr.innerHTML=""};
  dr.onclick=e=>{if(e.target===dr)close()};
  $("#mClose").onclick=close;
  $("#mToday").onclick=()=>{close();goDate(todayStr())};
  $("#mCal").onclick=()=>{close();openCalendar(S.date,goDate)};
  $("#mClients").onclick=()=>{close();openClients()};
  $("#mRem").onclick=()=>{close();openReminders(shiftDate(todayStr(),1))};
  $("#mAcc").onclick=()=>{close();openAccount()};
  let armedOut=false;$("#mOut").onclick=async()=>{if(!armedOut){armedOut=true;$("#mOut").lastChild.textContent="Apasă din nou pentru deconectare";return}
    close();await authApi.signOut()};
};

/* ---------- appointment sheet ---------- */
function timeOptions(sel){const s=toMin(S.hours.start),e=toMin(S.hours.end);let o="";
  for(let m=s;m<e;m+=STEP)o+=`<option ${toTime(m)===sel?"selected":""}>${toTime(m)}</option>`;
  if(sel&&(toMin(sel)<s||toMin(sel)>=e))o=`<option selected>${sel}</option>`+o;return o}
function openAppt(a,pre){
  const isNew=!a;a=a||{date:S.date,staff:pre.staff,start:pre.start,dur:30,client:"",phone:"",service:"",note:""};
  let staff=a.staff,date=a.date;
  const m=$("#modal");
  m.innerHTML=`<div class="sheet compact" role="dialog" aria-modal="true" aria-labelledby="mTitle">
    <div class="sheet-head"><h2 id="mTitle">${isNew?"Programare nouă":"Programare"}</h2>
      <button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button></div>
    <div class="sheet-body">
    <form class="cform" id="apForm" autocomplete="off">
      <div class="trio">
        <div class="field"><span class="lbl">Data</span><button type="button" class="pick" id="fDate"></button></div>
        <div class="field"><label for="fStart">Ora</label><select id="fStart">${timeOptions(a.start)}</select></div>
        <div class="field"><label for="fDur">Durată</label><select id="fDur">${durOptions(a.dur||30)}</select></div>
      </div>
      <div class="field"><span class="lbl">Lucrătoare</span><div class="staffpick" id="fStaff">${STAFF.map(s=>`<button type="button" data-id="${s.id}" aria-pressed="${s.id===staff}" style="border-bottom-color:${s.color}">${s.name}</button>`).join("")}</div></div>
      <div class="field"><span class="lbl">Serviciu</span><button type="button" class="pick" id="fSvc"></button></div>
      <div class="field"><span class="lbl">Status</span><div class="stpick" id="fStatus">${STATUSES.map(([k,l])=>`<button type="button" class="st-${k}" data-s="${k}" aria-pressed="${k===(a.status||"acceptata")}">${l}</button>`).join("")}</div></div>
      <div class="field"><label for="fClient">Clientă și telefon</label>
        <div style="position:relative" id="cliField"><input id="fClient" value="${esc(clientLine(a))}" placeholder="Ex: Maria Pop 0744 123 456" autocapitalize="words" role="combobox" aria-expanded="false" aria-controls="cliList">
        <div class="combo-list up" id="cliList" hidden></div></div>
        <div class="crow" id="contact"><a class="call" id="cCall" href="#">${ICON.call}Sună</a><a class="sms" id="cSms" href="#">${ICON.sms}SMS</a><a class="smsd" id="cSmsD" href="#">${ICON.sms}SMS detaliat</a><a class="wa" id="cWa" href="#" target="_blank" rel="noopener">${ICON.wa}WhatsApp</a></div></div>
      <div class="field"><label for="fNote">Notițe</label><input id="fNote" value="${esc(a.note)}" placeholder="Opțional"></div>
      <div class="warn" id="fWarn" hidden></div>
    </form>
    </div>
    <div class="sheet-foot">
      ${isNew?"":`<button type="button" class="btn danger" id="delBtn">Șterge</button>`}
      <button type="button" class="btn" id="cancelBtn">Renunță</button>
      <button type="submit" form="apForm" class="btn primary" id="saveBtn">Salvează</button>
    </div></div>`;
  m.hidden=false;lockScroll(true);
  const f=id=>m.querySelector(id);

  /* date */
  const showDate=()=>{f("#fDate").textContent=fmtDate(date,{weekday:"short",day:"numeric",month:"short"}).replace(/\./g,"")};showDate();
  f("#fDate").onclick=()=>openCalendar(date,d=>{date=d;showDate();check();updContact()});

  /* staff */
  f("#fStaff").querySelectorAll("button").forEach(b=>b.onclick=()=>{staff=b.dataset.id;f("#fStaff").querySelectorAll("button").forEach(x=>x.setAttribute("aria-pressed",x===b));
    const cur=svcIn.value.trim().toLowerCase();if(cur&&!isBlock(cur)&&!S.services.some(s=>s.staff===staff&&s.name.toLowerCase()===cur))svcIn.value="";showSvc();
    check();updContact()});

  /* searchable service list */
  const svcIn={value:a.service||""};
  const showSvc=()=>{const b=f("#fSvc");b.textContent=svcIn.value||"Alege serviciul";b.style.color=svcIn.value?"":"var(--muted)";b.style.fontWeight=svcIn.value?"600":"400";b.style.textTransform="none"};showSvc();
  f("#fSvc").onclick=()=>openServicePicker(staff,svcIn.value,s=>{svcIn.value=s.name;if(s.dur)f("#fDur").innerHTML=durOptions(s.dur);showSvc();check();updContact()});

  /* contact buttons */
  const current=()=>({...splitClient(f("#fClient").value),date,start:f("#fStart").value,staff,service:svcIn.value.trim()});
  const updContact=()=>{const c=current(),ok=digits(c.phone).length>=6;f("#contact").setAttribute("aria-disabled",String(!ok));if(!ok)return;
    const msg=reminder({client:c.name,date:c.date,start:c.start,staff:c.staff,service:c.service});
    f("#cCall").href="tel:"+digits(c.phone);
    f("#cSms").href="sms:"+digits(c.phone)+"?&body="+encodeURIComponent(msg);
    f("#cSmsD").href="sms:"+digits(c.phone)+"?&body="+encodeURIComponent(detailed({client:c.name,date:c.date,start:c.start,staff:c.staff,service:c.service}));
    f("#cWa").href="https://wa.me/"+intl(c.phone)+"?text="+encodeURIComponent(msg)};
  f("#fClient").addEventListener("input",updContact);f("#fStart").addEventListener("change",updContact);

  /* client suggestions: saved clients + imported phone contacts */
  const cliIn=f("#fClient"),cliList=f("#cliList");
  const closeCli=()=>{cliList.hidden=true;cliIn.setAttribute("aria-expanded","false")};
  const drawCli=()=>{const q=cliIn.value.trim();if(q.length<2){closeCli();return}
    const hits=searchPeople(q,8);if(!hits.length){closeCli();return}
    cliList.innerHTML=hits.map((p,i)=>`<button type="button" data-i="${i}">${esc(p.name)}<span class="num">${esc(p.phone)}</span></button>`).join("");
    cliList.querySelectorAll("button").forEach(b=>b.onclick=()=>{const p=hits[+b.dataset.i];cliIn.value=p.phone?p.name+" "+p.phone:p.name;closeCli();updContact()});
    cliList.hidden=false;cliIn.setAttribute("aria-expanded","true")};
  cliIn.addEventListener("input",drawCli);
  m.onpointerdown=e=>{const c=f("#cliField");if(c&&!c.contains(e.target))closeCli()};

  /* status */
  let status=a.status||"acceptata";
  f("#fStatus").querySelectorAll("button").forEach(b=>b.onclick=()=>{status=b.dataset.s;f("#fStatus").querySelectorAll("button").forEach(x=>x.setAttribute("aria-pressed",x===b))});
  /* remember what was sent (existing appointments only) */
  if(!isNew){const now=()=>new Date().toISOString(),ref=()=>S.db.collection("appointments").doc(a.id);
    f("#cSms").addEventListener("click",()=>ref().update({remindedAt:now(),remindedVia:"sms"}).catch(()=>{}));
    f("#cSmsD").addEventListener("click",()=>ref().update({confirmRequestedAt:now(),confirmRequestedVia:"manual"}).catch(()=>{}));
    f("#cWa").addEventListener("click",()=>ref().update({remindedAt:now(),remindedVia:"wa"}).catch(()=>{}));}

  /* overlap check */
  const check=()=>{const s=toMin(f("#fStart").value),e=s+(Number(f("#fDur").value)||30);
    const clash=(date===S.date?S.appts:[]).find(x=>x.id!==a.id&&x.staff===staff&&x.status!=="anulata"&&toMin(x.start)<e&&toMin(x.start)+(x.dur||30)>s);
    const w=f("#fWarn");w.hidden=!clash;if(clash)w.textContent=`Se suprapune cu ${clash.client} (${clash.start}) la ${staffName(staff)}.`};
  ["#fStart","#fDur"].forEach(id=>f(id).addEventListener("change",check));check();updContact();

  if(!isNew){let armed=false;f("#delBtn").onclick=async()=>{if(!armed){armed=true;f("#delBtn").textContent="Sigur?";return}
      try{await S.db.collection("appointments").doc(a.id).delete();close();toast("Programare ștearsă")}catch(e){toast("Nu s-a putut șterge ("+e.code+")")}}}
  f("#cancelBtn").onclick=close;f("#xBtn").onclick=close;
  f("#apForm").onsubmit=async e=>{e.preventDefault();if(!S.db){toast("Salvarea nu e disponibilă aici");return}
    const cp=splitClient(f("#fClient").value);
    if(!cp.name&&!isBlock(svcIn.value)){f("#fClient").focus();toast("Scrie numele clientei");return}
    const now=new Date().toISOString();
    const data={date,staff,start:f("#fStart").value,dur:Number(f("#fDur").value)||30,
      client:cp.name,phone:cp.phone,service:svcIn.value.trim(),status,note:f("#fNote").value.trim(),updatedAt:now,createdAt:a.createdAt||now};
    f("#saveBtn").disabled=true;
    try{let id=a.id;if(isNew)id=(await S.db.collection("appointments").add(data)).id;else{const {id:_omit,...prev}=a;await S.db.collection("appointments").doc(a.id).set({...prev,...data})}
      await saveClient(data).catch(()=>{});if(data.date!==S.date)goDate(data.date);
      if(isNew&&digits(data.phone).length>=6&&!isBlock(data.service)){showAccepted(id,data);return}
      close();toast("Programare salvată")}
    catch(err){f("#saveBtn").disabled=false;toast(err.code==="permission-denied"?"Nu ai drept de modificare":"Nu s-a salvat ("+err.code+")")}};
  function close(){m.hidden=true;m.innerHTML="";lockScroll(false)}
}
document.addEventListener("keydown",e=>{if(e.key!=="Escape")return;
  for(const id of ["#calModal","#drawer","#modal"]){const el=$(id);if(!el.hidden){el.hidden=true;el.innerHTML="";break}}});

async function saveClient(d){const key=digits(d.phone);if(key.length<6)return;
  const ex=S.clients.find(c=>c.id===key);if(ex&&ex.name===d.client)return;
  await S.db.collection("clients").doc(key).set({name:d.client,phone:d.phone,createdAt:ex?.createdAt||new Date().toISOString()})}

/* ---------- service picker: search box on top, list right under it (stays above the phone keyboard) ---------- */
function openServicePicker(staff,current,onPick){
  const m=$("#calModal");
  m.innerHTML=`<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="spTitle">
    <div class="sheet-head"><h2 id="spTitle">Serviciu · ${esc(staffName(staff))}</h2><button type="button" class="btn ghost icon" id="spX" aria-label="Închide">✕</button></div>
    <div style="padding:12px 16px 8px"><input id="spQ" class="search" type="search" placeholder="Caută serviciul" autocomplete="off"></div>
    <div class="sheet-body" id="spList" style="padding-top:0"></div></div>`;
  m.hidden=false;lockScroll(true);
  const close=()=>{m.hidden=true;m.innerHTML="";if($("#modal").hidden)lockScroll(false)};
  m.onclick=e=>{if(e.target===m)close()};$("#spX").onclick=close;
  const draw=()=>{const raw=$("#spQ").value.trim(),q=fold(raw);
    const items=S.services.filter(s=>(!s.staff||s.staff===staff)&&(!q||fold(s.name).includes(q)));
    let h=items.map((s,i)=>`<button type="button" class="row" data-i="${i}" style="width:100%;border-left:0;border-right:0;border-bottom:0;background:${s.name===current?"var(--accent-soft)":"transparent"};text-align:left;min-height:52px;font-size:16px;padding:12px 6px"><span class="main"><b style="font-weight:${s.name===current?700:500}">${esc(s.name)}</b></span><span class="sub num">${durLabel(s.dur)}</span></button>`).join("");
    if(raw&&!items.some(s=>fold(s.name)===q))h+=`<button type="button" class="row" id="spCustom" style="width:100%;border-left:0;border-right:0;border-bottom:0;background:transparent;text-align:left;min-height:52px;font-size:16px;padding:12px 6px;color:var(--accent)"><span class="main"><b>Folosește „${esc(raw)}”</b></span></button>`;
    if(!h)h=`<div class="empty">Nu sunt servicii pentru ${esc(staffName(staff))}.</div>`;
    $("#spList").innerHTML=h;
    $("#spList").querySelectorAll("[data-i]").forEach(b=>b.onclick=()=>{close();onPick(items[+b.dataset.i])});
    const c=$("#spCustom");if(c)c.onclick=()=>{close();onPick({name:raw})}};
  $("#spQ").oninput=draw;draw();
}

/* ---------- after saving a new appointment: send the confirmation from the stylist's own phone ---------- */
function confirmation(a){return `Bună ziua${a.client?", "+a.client:""}! Programarea dvs. la Salon Vintage a fost acceptată: ${fmtDate(a.date)}, ora ${a.start}, la ${staffName(a.staff)}${a.service?" ("+a.service+")":""}. Vă așteptăm!`}
function showAccepted(id,a){
  const m=$("#modal"),msg=confirmation(a),num=digits(a.phone);
  m.innerHTML=`<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="okTitle">
    <div class="sheet-head"><h2 id="okTitle">✓ Programare salvată</h2><button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button></div>
    <div class="sheet-body">
      <p style="margin:0 0 6px"><b>${esc(a.client)}</b> · <span class="num">${esc(a.phone)}</span></p>
      <p class="sub" style="margin:0 0 14px;color:var(--muted)">Trimite-i confirmarea de pe telefonul tău. Se deschide mesajul gata scris, tu apeși doar „Trimite”.</p>
      <div style="background:var(--surface-2);border:1px solid var(--line);border-radius:12px;padding:12px;font-size:15px">${esc(msg)}</div>
      <div style="display:grid;gap:10px;margin-top:16px">
        <a class="btn primary" style="min-height:58px;font-size:17px" id="okSms" href="sms:${esc(num)}?&body=${encodeURIComponent(msg)}">${ICON.sms.replace("<svg",'<svg width="22" height="22"')} Trimite SMS</a>
        <a class="btn" style="min-height:58px;font-size:17px;color:var(--wa);border-color:var(--wa)" id="okWa" href="https://wa.me/${esc(intl(a.phone))}?text=${encodeURIComponent(msg)}" target="_blank" rel="noopener">${ICON.wa.replace("<svg",'<svg width="22" height="22"')} Trimite pe WhatsApp</a>
        <button type="button" class="btn" id="okCopy">Copiază textul</button>
      </div>
    </div>
    <div class="sheet-foot"><button type="button" class="btn" id="okSkip">Gata</button></div></div>`;
  const close=()=>{m.hidden=true;m.innerHTML="";lockScroll(false)};
  const mark=via=>S.db.collection("appointments").doc(id).update({confirmSentAt:new Date().toISOString(),confirmVia:via}).catch(()=>{});
  $("#okSms").addEventListener("click",()=>{mark("sms");setTimeout(close,400)});
  $("#okWa").addEventListener("click",()=>{mark("wa");setTimeout(close,400)});
  $("#okCopy").onclick=()=>copyText(msg);
  $("#xBtn").onclick=close;$("#okSkip").onclick=close;
}

/* ---------- people search: clients saved from earlier appointments ---------- */
function fold(s){return String(s||"").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g,"")}
function searchPeople(q,max){const fq=fold(q),dq=digits(q),seen=new Set(),out=[];
  const test=p=>(fold(p.name).includes(fq))||(dq.length>=3&&digits(p.phone).includes(dq));
  for(const src of [S.clients])for(const p of src){if(out.length>=max)return out;
    const k=digits(p.phone)||fold(p.name);if(seen.has(k)||!test(p))continue;seen.add(k);out.push({name:p.name,phone:p.phone||""})}
  return out}

/* ---------- reminders (from the menu) ---------- */
function openReminders(day){
  const m=$("#modal");let unsub=null,rows=[];
  m.innerHTML=`<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="rTitle">
    <div class="sheet-head"><h2 id="rTitle">Reamintiri</h2><button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button></div>
    <div class="sheet-body">
      <div class="field"><span class="lbl">Programări din ziua</span><button type="button" class="pick" id="rDate"></button></div>
      <p class="hint" id="rCount" style="margin:10px 0 0"></p>
      <div id="rList"></div>
    </div></div>`;
  m.hidden=false;lockScroll(true);
  const close=()=>{if(unsub)unsub();m.hidden=true;m.innerHTML="";lockScroll(false)};
  $("#xBtn").onclick=close;
  const mark=async(id,via)=>{const t=new Date().toISOString();try{await S.db.collection("appointments").doc(id).update(via==="smsd"?{remindedAt:t,remindedVia:"sms",confirmRequestedAt:t,confirmRequestedVia:"manual"}:{remindedAt:t,remindedVia:via})}catch(e){}};
  const draw=()=>{const el=$("#rList");if(!el)return;
    const list=rows.filter(a=>!isBlock(a.service)).sort((x,y)=>x.start.localeCompare(y.start));
    const withPhone=list.filter(a=>digits(a.phone).length>=6),sent=withPhone.filter(a=>a.remindedAt).length;
    $("#rCount").textContent=list.length?`${sent} din ${withPhone.length} trimise${list.length>withPhone.length?` · ${list.length-withPhone.length} fără telefon`:""}`:"";
    if(!list.length){el.innerHTML=`<div class="empty">Nu sunt programări în ziua aceasta.</div>`;return}
    el.innerHTML=list.map(a=>{const ok=digits(a.phone).length>=6,msg=reminder(a);
      return `<div class="row"><div class="main"><b>${esc(a.client||"Fără nume")}</b><span class="sub"><span class="num">${esc(a.start)}</span> · ${esc(staffName(a.staff))}${a.service?" · "+esc(a.service):""}</span>
        <span class="sub" style="display:block;color:${a.status==="confirmata"?"var(--ok)":a.status==="anulata"?"var(--bad)":a.remindedAt?"var(--ok)":"var(--muted)"}">${a.status==="confirmata"?"✓ Confirmată de clientă":a.status==="anulata"?"✕ Anulată de clientă":a.confirmRequestedAt?"⏳ Așteaptă răspuns 1/0"+(a.confirmRequestedVia==="auto"?" (trimis automat)":""):a.remindedAt?"✓ Trimis "+(a.remindedVia==="wa"?"pe WhatsApp":"prin SMS"):ok?esc(a.phone):"Fără număr de telefon"}</span></div>
        ${ok?`<div class="mini"><a class="sms" style="color:var(--accent)" href="sms:${esc(digits(a.phone))}?&body=${encodeURIComponent(msg)}" data-id="${esc(a.id)}" data-via="sms" aria-label="Trimite SMS">${ICON.sms}</a><a class="smsd" style="color:#fff;background:var(--accent);border-color:var(--accent);font-weight:700;font-size:11px;width:auto;padding:0 8px" href="sms:${esc(digits(a.phone))}?&body=${encodeURIComponent(detailed(a))}" data-id="${esc(a.id)}" data-via="smsd" aria-label="Trimite SMS detaliat">1/0</a><a class="wa" href="https://wa.me/${esc(intl(a.phone))}?text=${encodeURIComponent(msg)}" target="_blank" rel="noopener" data-id="${esc(a.id)}" data-via="wa" aria-label="Trimite pe WhatsApp">${ICON.wa}</a></div>`:""}</div>`}).join("");
    el.querySelectorAll("[data-via]").forEach(l=>l.addEventListener("click",()=>mark(l.dataset.id,l.dataset.via)))};
  const load=()=>{$("#rDate").textContent=relDay(day)+" · "+fmtDate(day,{weekday:"long",day:"numeric",month:"long"});
    if(unsub)unsub();rows=[];$("#rList").innerHTML=`<div class="empty">Se încarcă…</div>`;
    if(!S.db){$("#rList").innerHTML=`<div class="empty">Datele nu sunt disponibile aici.</div>`;return}
    unsub=S.db.collection("appointments").where("date","==",day).onSnapshot(s=>{rows=s.docs.map(d=>({id:d.id,...d.data()}));draw()},()=>{})};
  $("#rDate").onclick=()=>openCalendar(day,d=>{day=d;load()});
  load();
}

/* ---------- client search (from the menu) ---------- */
function openClients(){
  const m=$("#modal");
  m.innerHTML=`<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="cTitle">
    <div class="sheet-head"><h2 id="cTitle">Cliente</h2><button type="button" class="btn ghost icon" id="xBtn" aria-label="Închide">✕</button></div>
    <div class="sheet-body"><input id="clientSearch" class="search" type="search" placeholder="Caută nume sau telefon"><div id="clientList"></div></div></div>`;
  m.hidden=false;lockScroll(true);
  const close=()=>{m.hidden=true;m.innerHTML="";lockScroll(false)};
  $("#xBtn").onclick=close;
  const draw=()=>{const q=$("#clientSearch").value.trim().toLowerCase(),qd=digits(q);
    const list=S.clients.filter(c=>!q||String(c.name).toLowerCase().includes(q)||(qd&&digits(c.phone).includes(qd))).sort((a,b)=>String(a.name).localeCompare(String(b.name),"ro"));
    const el=$("#clientList");
    if(!S.clients.length){el.innerHTML=`<div class="empty">Încă nu sunt cliente salvate. Fiecare programare cu număr de telefon o adaugă aici automat.</div>`;return}
    if(!list.length){el.innerHTML=`<div class="empty">Nicio clientă nu se potrivește.</div>`;return}
    el.innerHTML=list.map(c=>`<div class="row"><div class="main" data-hist="${esc(c.id)}" role="button" tabindex="0"><b>${esc(c.name)}</b><span class="sub num">${esc(c.phone)} · istoric ▾</span></div>
      <div class="mini"><a class="call" href="tel:${esc(digits(c.phone))}" aria-label="Sună">${ICON.call}</a><a class="wa" href="https://wa.me/${esc(intl(c.phone))}" target="_blank" rel="noopener" aria-label="WhatsApp">${ICON.wa}</a></div></div><div id="h-${esc(c.id)}" hidden></div>`).join("");
    el.querySelectorAll("[data-hist]").forEach(b=>b.onclick=async()=>{const box=$("#h-"+CSS.escape(b.dataset.hist));
      if(!box.hidden){box.hidden=true;return}box.hidden=false;box.innerHTML=`<div class="row sub">Se încarcă…</div>`;
      const c=S.clients.find(x=>x.id===b.dataset.hist);
      try{const s=await S.db.collection("appointments").where("phone","==",c.phone).get();
        const items=s.docs.map(d=>d.data()).sort((x,y)=>(y.date+y.start).localeCompare(x.date+x.start));
        box.innerHTML=items.length?items.map(x=>`<div class="row" style="padding-left:16px"><div class="main"><b class="num">${esc(fmtDate(x.date,{day:"numeric",month:"short",year:"numeric"}))} · ${esc(x.start)}</b><span class="sub">${esc(x.service||"—")} · ${esc(staffName(x.staff))}${x.note?" · "+esc(x.note):""}</span></div></div>`).join(""):`<div class="row sub" style="padding-left:16px">Nicio programare încă.</div>`}
      catch(e){box.innerHTML=`<div class="row sub">Istoricul nu s-a putut încărca.</div>`}});};
  $("#clientSearch").oninput=draw;draw();
}


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
