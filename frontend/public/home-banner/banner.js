(() => {
  'use strict';
  // i18n: the parent app (HomeView) posts translated strings via
  // postMessage({type:'home-banner-i18n', messages}) — stored on
  // window.BANNER_I18N and read at call time (applies from the next action).
  window.addEventListener('message', (e) => {
    const d = e.data;
    if (d?.type === 'home-banner-filter-right') {
      // HomeView posts the right edge of the Chart-tab filter controls
      // (TIME RANGE / TYPE), relative to the banner iframe (null = absent).
      filterRight = typeof d.right === 'number' ? d.right : null;
      return;
    }
    if (d?.type !== 'home-banner-i18n') return;
    window.BANNER_I18N = d.messages;
  });
  const i18nMsg = () => window.BANNER_I18N || {};
  // True once HomeView has posted at least one translated message — lets the
  // robot distinguish "i18n not loaded yet" (use the English fallback) from
  // "the key was deleted in Message Term" (stay silent instead).
  const i18nLoaded = () => i18nMsg().loaded === true;
  // filterRight: right edge (px, relative to the banner) of the Chart-tab
  // filter controls, posted by HomeView — the speech bubble must not overlap
  // them (null = Chart tab not active / filter bar absent).
  let filterRight=null;
  const banner=document.querySelector('.ai-banner');
  if(!banner)return;
  const $=s=>banner.querySelector(s);
  const position=$('.robot-position'),travel=$('.robot-travel'),robot=$('.robot'),rig=$('.robot-rig');
  const turn=$('.robot-turn'),head=$('.robot-head'),torso=$('.skeleton-torso');
  const armR=$('.robot-arm'),armL=$('.robot-arm-left'),speech=$('.speech'),particles=$('.particles');
  const media=matchMedia('(prefers-reduced-motion: reduce)');
  const clamp=(n,a,b)=>Math.max(a,Math.min(b,n)),rand=(a,b)=>a+Math.random()*(b-a);
  const spring=value=>({p:value,v:0});
  // All joints preserve position AND velocity when an action changes.
  function advance(s,target,k,d,dt){s.v+=((target-s.p)*k-s.v*d)*dt;s.p+=s.v*dt;}
  const state={x:spring(0),y:spring(0),z:spring(.76),bank:spring(0),body:spring(0),yaw:spring(0),roll:spring(0),pitch:spring(0),left:spring(0),right:spring(0),eyeX:spring(0),eyeY:spring(0),warm:spring(0),blink:spring(0),squash:spring(0)};
  const profiles={
    curious:{label:'Curious',speed:[.65,1.05],glow:'#70f5ff'},
    playful:{label:'Playful',speed:[1.45,2.3],glow:'#fa86e7'},
    excited:{label:'Excited',speed:[1.15,1.9],glow:'#d799ff'},
    dreamy:{label:'Relaxed',speed:[.32,.58],glow:'#aaa2ff'},
    affectionate:{label:'Feeling loved',speed:[.55,.9],glow:'#ff9eda'},
    surprised:{label:'Surprised',speed:[1.4,2.1],glow:'#c2ffff'}
  };
  const anchors=[[.18,.28,.4],[.43,.72,.6],[.83,.25,.35],[.55,.48,.95],[.18,.68,.48],[.83,.63,.75]];
  let geometry,rect,goal,routeIndex=0,clock=0,lastFrame=0,raf=0;
const velSm={x:0,y:0};let intentSm=0,sideX=null;
  let reduced=media.matches,sleeping=false,drag=null,suppressClick=false;
  let action=null,nextAction=1400,nextGoal=0;
let sleepStart=0,sleepDuration=0,lastSleepStart=0,blinkNext=4000,blinkStart=-1;
  let speed=.85,targetSpeed=.85,mood='curious',moodUntil=0,nextMood=8500,energy=.85,lastInteraction=-10000;
  let pointer=null,pointerLast=-10000,lastSurprise=-10000,lastTrail=0,history=[],greeted=false,greetingUsed=false,introduced=false,hasWritten=false,roamUntil=0,flightPlan=null,idleSince=null;
  const slogans=["AI opens doors. People lead the way.","Today's technology. Tomorrow's possibilities.","Create the future with artificial intelligence.","Turn bright ideas into real possibilities.","The future begins with curiosity."];
  let sloganIndex=0;
  // Slogan speech: the robot flies to the (former) board spot and says the
  // next slogan in its speech bubble — the teacher-board writing animation
  // was removed, so the slogan is no longer drawn on a board.
  function saySlogan(){
    const i18nSlogans=(i18nMsg().slogans||[]).filter(s=>typeof s==='string'&&s.length);
    const pool=i18nSlogans.length?i18nSlogans:slogans;
    const text=pool[sloganIndex++%pool.length];
    say(text,5200,true);
  }
  let speechTimer,loveTimer,bubbleW=0,sleepLineTimer=null,sleepLineIndex=0;
  const active=()=>!reduced&&!sleeping&&!document.hidden;
  // Sleep speech: while the robot sleeps the bubble may ONLY show
  // sleep-related lines — the i18n home.banner.speech.sleep value (e.g.
  // "Đang nạp năng lượng... z Z Z") cycled with "z Z Z". Every other phrase
  // is suppressed while sleeping (say() refuses to speak).
  function saySleepLine(){
    if(!sleeping||reduced||document.hidden)return;
    const v=(i18nMsg().speech||{}).sleep;
    const pool=[v!==undefined&&v!==''?v:(i18nLoaded()?'':'Recharging my energy... z Z Z'),'z Z Z','Z z z...'].filter(Boolean);
    const text=pool[sleepLineIndex++%pool.length];
    if(text){
      clearTimeout(speechTimer);speech.textContent=text;speech.classList.remove('slogan');speech.classList.add('visible');
      bubbleW=speech.offsetWidth;
      speechTimer=setTimeout(()=>speech.classList.remove('visible'),2600);
    }
    sleepLineTimer=setTimeout(saySleepLine,4200);
  }
  function moodTo(name,hold=0){
    mood=name;moodUntil=clock+hold;nextMood=clock+rand(6500,11000);
    const p=profiles[name];targetSpeed=rand(...p.speed);banner.dataset.emotion=name;
  }
  function say(text,duration=2400,isSlogan=false){
    if(!text||sleeping)return;
    clearTimeout(speechTimer);speech.textContent=text;speech.classList.toggle('slogan',isSlogan);speech.classList.add('visible');
    bubbleW=speech.offsetWidth;
    speechTimer=setTimeout(()=>speech.classList.remove('visible'),duration);
  }
  // i18n speech: translated line for a named event, falls back to the
  // original English text only before the first i18n load. After a load, a
  // missing/empty value means the key was deleted in Message Term → stay
  // silent (no fallback), so deletions are applied.
  function sayI18n(key,fallback,duration){
    const s=i18nMsg().speech||{};
    const v=s[key];
    say(v!==undefined&&v!==''?v:(i18nLoaded()?'':fallback),duration);
  }
  function limits(z){
    const g=geometry,pad=12;
    const maxZ=Math.min(.92,(g.width-pad*2)/(g.w*1.56),(g.height-pad*2)/(g.h*1.46));
    z=clamp(z,.24,maxZ);
    return {z,hx:g.w*z*.78+pad,hy:g.h*z*.73+pad};
  }
  function measure(){
    const old=geometry;
    rect=banner.getBoundingClientRect();
    geometry={width:banner.clientWidth,height:banner.clientHeight,w:position.offsetWidth,h:position.offsetHeight};
    if(old){state.x.p*=geometry.width/old.width;state.y.p*=geometry.height/old.height;state.x.v*=geometry.width/old.width;state.y.v*=geometry.height/old.height;if(sideX!=null)sideX*=geometry.width/old.width;}
    else{state.x.p=geometry.width*.76;state.y.p=geometry.height*.5;sideX=state.x.p;}
    state.z.p=limits(state.z.p).z;goal=null;keepInside();render();
  }
  function keepInside(){
    const l=limits(state.z.p),g=geometry;state.z.p=l.z;
    for(const [axis,min,max] of [['x',l.hx,g.width-l.hx],['y',l.hy,g.height-l.hy]]){
      const s=state[axis],p=clamp(s.p,min,max);if(p!==s.p){s.p=p;if((p===min&&s.v<0)||(p===max&&s.v>0))s.v*=.15;}
    }
  }
  function pickGoal(force=false){
    if(!force&&clock>=roamUntil){goal={x:geometry.width*.78+60,y:geometry.height*.52,z:.82,at:clock,phase:0,roaming:false};nextGoal=Infinity;return;}
    const a=anchors[routeIndex++%anchors.length];
    let nx=a[0]+rand(-.05,.05),ny=a[1]+rand(-.06,.06),z=a[2]*rand(.94,1.06);
    if(mood==='curious'&&pointer&&routeIndex%3===0){nx=pointer.nx;ny=pointer.ny;z=.82;}
    goal={x:geometry.width*nx,y:geometry.height*ny,z,at:clock,phase:rand(0,6),roaming:true};
    nextGoal=clock+1000;
  }
  function choose(){
    if(!greeted){greeted=true;return 'wave';}
    if(!hasWritten){hasWritten=true;return 'write';}
    if(!introduced){introduced=true;return 'inspect';}
    const kinds={curious:['inspect','write','inspect','turn-left'],playful:['write','wave','inspect','sway'],excited:['inspect','write','wave','roam'],dreamy:['rest','inspect','write'],affectionate:['wave','write','inspect'],surprised:['inspect','rest']}[mood];
    const fresh=kinds.filter(k=>!history.slice(-2).includes(k)),pool=fresh.length?fresh:kinds;
    return pool[Math.floor(Math.random()*pool.length)];
  }
  function perform(kind,manual=false){
    if(reduced&&kind==='write'){hasWritten=true;saySlogan();return;}
    if(!active())return;
    if(kind!=='roam'&&kind!=='dash'){flightPlan=null;roamUntil=0;if(goal?.roaming)pickGoal();}
    const durations={wave:3000,curious:3400,'turn-left':3000,'turn-right':3000,hop:2600,peek:2400,sway:3000,love:1200,dash:4500,rest:5000,inspect:7500,write:6000,roam:9800};
    action={kind,start:clock,duration:durations[kind]||2600};
    history.push(kind);if(history.length>6)history.shift();nextAction=clock+action.duration+rand(2800,5700);
    if(kind==='wave'&&!hasWritten)nextAction=clock+3400;
    if(manual){lastInteraction=clock;nextAction=clock+action.duration+5000;}
    // First wave greets the logged-in user by name (HomeView substitutes
    // {name} before posting); later waves keep the generic phrase. If the
    // greeting key was deleted in Message Term, don't retry — the generic
    // wave phrase is used instead.
    const i18nGreeting=i18nMsg().greeting;
    if(kind==='wave'&&!greetingUsed){
      greetingUsed=true;
      if(typeof i18nGreeting==='string'&&i18nGreeting){say(i18nGreeting,4200);return;}
    }
    const phrases={
      wave:"Hello! Over here! ^^",curious:"Can I take a closer look? 👀",
      'turn-left':"A little turn to the left! ↶",'turn-right':"Turning right! ↷",
      hop:"Let's take a little flight! ✨",peek:"Peekaboo! Still there? ♡",
      sway:"La la la... what a lovely day! ♫",dash:"Whoosh! Try to catch me! ✨",
      rest:"Just watching the world... ☁",love:"Hehe, that tickles! ♡",inspect:"Let me find some ideas in these panels...",
      roam:"A little flight, then I'll be back!"
    };
    // Action kind → home.banner.speech.* key suffix (HomeView posts every
    // such key in the flat `speech` map, so a phrase added in Translation
    // Management is spoken automatically). After an i18n load, a missing
    // value means the key was deleted → stay silent (no English fallback).
    const speechKey={wave:'wave',curious:'curious','turn-left':'turnLeft','turn-right':'turnRight',hop:'hop',peek:'peek',sway:'sway',dash:'dash',rest:'rest',love:'love',inspect:'inspect',write:'write',roam:'roam'};
    const i18nSpeech=i18nMsg().speech||{};
    const phraseFor=(k)=>{
      const v=i18nSpeech[speechKey[k]];
      if(v!==undefined&&v!=='')return v;
      return i18nLoaded()?'':(phrases[k]||phrases.sway);
    };
    if(kind==='write'){
      hasWritten=true;
      // Mostly a slogan, but sometimes a random friendly phrase instead —
      // keeps the speech bubble varied rather than slogan-only. The pool is
      // every home.banner.speech.* value posted by HomeView (falls back to
      // the built-in English phrases before the first i18n load).
      if(Math.random()<.7) saySlogan();
      else{
        const pool=i18nMsg().phrasePool||[];
        const list=pool.length?pool:Object.values(phrases);
        say(list[Math.floor(Math.random()*list.length)],5200);
      }
    }
    else say(phraseFor(kind),Math.min(action.duration,2800));
    if(kind==='curious')moodTo('curious',3500);
    if(kind==='dash'||kind==='roam'){
      moodTo('playful',4500);targetSpeed=kind==='dash'?2.4:1.2;
      const duration=kind==='dash'?4500:9600;roamUntil=clock+duration;
      flightPlan={start:clock,duration,index:-1,stages:[[.25,.31,.30],[.52,.62,.47],[.77,.48,.92]]};
    }
    if(kind==='write'){
      // Random slogan spot: stay in place, fly to the center, or fly to a
      // random spot — flying to the center every time looked mechanical.
      const r=Math.random();
      let gx,gy,gz;
      if(r<.4){gx=state.x.p;gy=state.y.p;gz=state.z.p;}
      else if(r<.7){gx=geometry.width*.42;gy=geometry.height*.5;gz=.55;}
      else{gx=geometry.width*rand(.18,.78);gy=geometry.height*rand(.3,.7);gz=rand(.5,.75);}
      roamUntil=0;goal={x:gx,y:gy,z:gz,at:clock,phase:0,roaming:false};nextGoal=Infinity;
    }
    if(kind==='inspect'){introduced=true;roamUntil=0;pickGoal();moodTo('curious',7800);}
    if(kind==='rest'){
      // 'curious' instead of 'dreamy': the dreamy mood squashes the eye
      // glints into thin lines, so the robot looked asleep while saying
      // "Just watching the world..." — the eyes must stay open. The slow
      // dreamy speed is set explicitly to keep the rest pose gentle.
      moodTo('curious',5000);targetSpeed=rand(...profiles.dreamy.speed);
      goal={x:state.x.p,y:state.y.p,z:state.z.p,at:clock,phase:0};nextGoal=clock+3500;
    }
  }
  function actionPose(){
    const out={yaw:0,roll:0,pitch:0,left:0,right:0,lift:0,zoom:0,squash:0};
    if(!action||sleeping)return out;
    const t=clamp((clock-action.start)/action.duration,0,1),e=Math.sin(Math.PI*t)**2;
    const kind=action.kind;
    if(t===1){action=null;return out;}
    if(kind==='wave'){out.right=-e*(76+23*Math.sin(t*Math.PI*7));out.left=e*6;out.roll=-e*3;out.pitch=e*Math.sin(t*Math.PI*2)*4;}
    if(kind==='curious'){out.zoom=e*.14;out.yaw=-e*8;out.roll=-e*5;out.pitch=e*6;out.left=e*5;out.right=-e*8;}
    if(kind==='turn-left'||kind==='turn-right'){const d=kind==='turn-left'?-1:1;out.yaw=d*28*e;out.roll=(d<0?13:-5)*e;out.left=d<0?e*23:0;out.right=d>0?-e*13:0;}
    if(kind==='hop'){out.lift=-geometry.height*.045*e*Math.sin(t*Math.PI*3);out.left=e*12;out.right=-e*11;out.roll=e*Math.sin(t*Math.PI*2)*6;}
    if(kind==='peek'){out.zoom=e*.08;out.roll=e*Math.sin(t*Math.PI*2)*7;out.pitch=e*8;}
    if(kind==='sway'){out.roll=e*Math.sin(t*Math.PI*3)*8;out.left=e*9;out.right=-e*9;}
    if(kind==='love'){out.lift=-geometry.height*.025*e;out.squash=e*Math.sin(t*Math.PI*2)*.035;out.pitch=e*5;out.left=e*9;out.right=-e*7;}
    if(kind==='dash'){out.pitch=-e*5;out.left=e*14;out.right=-e*14;}
    if(kind==='inspect'){out.yaw=Math.sin(t*Math.PI*3)*12*e;out.pitch=5*e;out.left=17*e;out.right=-17*e;}
    return out;
  }
  function updateMind(dt){
    // Auto-wake: a nap lasts sleepDuration (~30 s), then the robot opens its
    // eyes again. Checked before the active() guard because
    // active() is false while sleeping.
    if(sleeping&&clock-sleepStart>sleepDuration)setSleep(false);
    if(!active())return;
    energy=clamp(energy+(mood==='dreamy'?.042:-.0072*speed)*dt,.15,1);
    if(clock>nextMood&&clock>moodUntil){
      const choices=(energy<.35?['dreamy','curious']:clock-lastInteraction<9000?['playful','excited','affectionate','curious']:['curious','playful','dreamy','excited']).filter(n=>n!==mood);
      moodTo(choices[Math.floor(Math.random()*choices.length)]);
    }
    // Auto-nap: the robot only dozes off when it has been standing still at
    // its default spot for a long time (no action, no roaming, little
    // movement). Any interaction (click/drag) wakes it via wake(). Naps are
    // rare: at least 5 min between naps, and each nap lasts ~30 s.
    if(!sleeping&&!action&&!flightPlan&&clock>=roamUntil&&Math.hypot(state.x.v,state.y.v)<12){
      const g=geometry;
      const atDefault=Math.hypot(state.x.p-(g.width*.78+60),state.y.p-g.height*.52)<g.width*.03;
      if(atDefault){
        if(idleSince==null)idleSince=clock;
        else if(clock-idleSince>45000&&clock-lastSleepStart>300000){sleepStart=clock;sleepDuration=30000;setSleep(true);}
      }else idleSince=null;
    }else idleSince=null;
    speed+=(targetSpeed-speed)*(1-Math.exp(-dt*1.8));
    if(!drag&&clock>=nextAction&&action?.kind!=='write')perform(choose());
  }
  function simulate(dt){
    clock+=dt*1000;updateMind(dt);
    if(!goal)pickGoal();
    if(flightPlan&&clock<roamUntil){
      const index=Math.min(2,Math.floor((clock-flightPlan.start)/flightPlan.duration*3));
      if(index!==flightPlan.index){flightPlan.index=index;const [x,y,z]=flightPlan.stages[index];goal={x:geometry.width*x,y:geometry.height*y,z,at:clock,phase:0,roaming:true};}
    }else if(flightPlan){flightPlan=null;pickGoal();}
    if(goal.roaming&&clock>=roamUntil)pickGoal();
    const pose=actionPose(),g=geometry;
    const resting=sleeping||(action&&action.kind==='rest');
    const dist=Math.hypot(state.x.p-goal.x,state.y.p-goal.y);
    if(active()&&!drag&&!resting&&!flightPlan&&clock<roamUntil&&clock>nextGoal&&dist<g.width*.028&&Math.hypot(state.x.v,state.y.v)<25)pickGoal();
    let targetX=resting?state.x.p:goal.x,targetY=resting?state.y.p:goal.y;
    let targetZ=goal.z+pose.zoom;
    if(drag){
      targetX=drag.targetX;targetY=drag.targetY;
      // The Home strip is short: at the normal z the robot's collision box is
      // nearly as tall as the banner, leaving almost no vertical room (the
      // allowed Y range is empty → the robot looked "stuck" when dragging up
      // or down). While dragging, shrink the robot so its box fits the
      // requested Y (z floor .24 = minimum size); it regrows on the next
      // autonomous goal (pickGoal sets the anchor z).
      const fitY=Math.max(0,Math.min(drag.targetY,g.height-drag.targetY)-12)/(g.h*.73);
      targetZ=clamp(fitY,.24,limits(.92).z);
    }
    if(sleeping)targetZ=state.z.p;
    const safe=limits(targetZ);
    targetX=clamp(targetX,safe.hx,g.width-safe.hx);targetY=clamp(targetY,safe.hy,g.height-safe.hy);
    // z is driven by the goal, not by the robot's live position: computing
    // the room-scale from state.x.p made the size oscillate frame to frame
    // near the center (moving right grew the robot, the spring overshoot
    // shrank it again → visible jitter). The goal is fixed, so the z target
    // is stable; limits() still keeps it within the banner.
    const roomScale=Math.min((Math.min(goal.x,g.width-goal.x)-12)/(g.w*.78),(Math.min(goal.y,g.height-goal.y)-12)/(g.h*.73));
    targetZ=clamp(Math.min(safe.z,roomScale),.24,limits(.92).z);
    const cruiseK=resting?2:1.2+speed*1.4,k=drag?100:cruiseK,damping=drag?20:2*Math.sqrt(k)*1.05;
    const drift=resting?0:Math.sin(clock/2300+goal.phase)*g.height*.009;
    advance(state.x,targetX,k,damping,dt);advance(state.y,targetY+drift+pose.lift,k,damping,dt);
    advance(state.z,targetZ,8,8.6,dt);
    const velocityX=state.x.v/g.width,velocityY=state.y.v/g.height;
    // Clockwise in screen space means leaning right. The head and torso now
    // reinforce the same bank instead of cancelling each other out.
    // Velocity is low-pass filtered: the spring overshoots near the goal
    // (velocity spikes then flips sign), and driving the bank/yaw/pitch with
    // the raw velocity made the robot visibly twitch as it settled.
    velSm.x+=(velocityX-velSm.x)*(1-Math.exp(-dt*5));
    velSm.y+=(velocityY-velSm.y)*(1-Math.exp(-dt*5));
    // Intent (distance-to-goal direction) also flips sign on overshoot —
    // smooth it the same way, otherwise the bank/roll twitch on arrival.
    intentSm+=((targetX-state.x.p)/g.width-intentSm)*(1-Math.exp(-dt*5));
    const intent=clamp(intentSm,-1,1);
    // Slow low-pass of the robot's x position, used ONLY for the bubble-side
    // decision. The raw spring position settles with a small wobble near a
    // goal; feeding that raw value into the side flip made the bubble jump
    // from one side of the robot to the other every frame as the robot
    // crossed the threshold — the "giật" jitter. A ~0.4 s low-pass means the
    // bubble only flips once the robot has genuinely moved to a new side.
    if(sideX==null)sideX=state.x.p;
    sideX+=(state.x.p-sideX)*(1-Math.exp(-dt*2.5));
    const flightWeight=1;
    let eyeX=0,eyeY=0,look=0;
    if(pointer&&clock-pointerLast<6000){
      look=clamp((pointer.x-rect.left-state.x.p)/(g.width*.5),-1,1);
      eyeX=look*16;eyeY=clamp((pointer.y-rect.top-state.y.p)/(g.height*.8),-1,1)*12;
    }else{look=Math.sin(clock/3100)*.15;eyeX=look*5;eyeY=Math.sin(clock/4000)*.8;}
    const yawTarget=sleeping?0:clamp(velSm.x*100+look*9+pose.yaw,-32,32);
    advance(state.yaw,yawTarget,30,9,dt);
    advance(state.roll,sleeping?4:pose.roll+clamp(velSm.x*35+intent*5,-11,11)*flightWeight,25,8,dt);
    advance(state.pitch,sleeping?7:pose.pitch+clamp(velSm.y*38,-10,10)*flightWeight,26,8.5,dt);
    advance(state.bank,sleeping?0:clamp(velSm.x*115+intent*8,-20,20)*flightWeight,14,6.5,dt);
    advance(state.body,sleeping?2:pose.roll*.2+clamp(velSm.x*25,-7,7)*flightWeight+Math.sin(clock/2800)*.7,12,6,dt);
    const breath=Math.sin(clock/1700),flap=Math.sin(clock/1300);
    advance(state.left,sleeping?2:pose.left+breath*2.3+velSm.y*18+Math.abs(velSm.x)*16,28,7.8,dt);
    advance(state.right,sleeping?-2:pose.right-flap*2-velSm.y*15-Math.abs(velSm.x)*13,32,8,dt);
    advance(state.eyeX,sleeping?0:eyeX,55,14,dt);advance(state.eyeY,sleeping?0:eyeY,55,14,dt);
    advance(state.warm,sleeping?.15:clamp(-state.yaw.p/24,0,.9),8,5.8,dt);
    // Eyes close only while sleeping or during a short natural blink.
    // Blinks are rare (every 4–9 s, ~0.26 s long) so the robot never looks
    // like it is dozing off — the thin closed-eye shape appears ONLY in
    // these two cases.
    if(!sleeping){
      if(blinkStart<0){
        if(clock>=blinkNext){blinkStart=clock;blinkNext=clock+rand(4000,9000);}
      }else if(clock-blinkStart>260){blinkStart=-1;}
    }
    advance(state.blink,sleeping||blinkStart>=0?1:0,250,26,dt);
    advance(state.squash,pose.squash,35,9,dt);keepInside();
  }
  function render(){
    if(!geometry)return;
    const s=state,g=geometry;
    position.style.left=`${s.x.p.toFixed(2)}px`;position.style.top=`${s.y.p.toFixed(2)}px`;
    position.style.transform=`translate(-50%,-50%) scale(${s.z.p.toFixed(5)})`;
    const bob=reduced?0:Math.sin(clock/1700)*g.h*.007;
    turn.style.transform=`translateY(${bob.toFixed(2)}px) rotate(${s.bank.p.toFixed(3)}deg)`;
    rig.style.transform=`scale(${(1+s.squash.p).toFixed(4)},${(1-s.squash.p*.7).toFixed(4)})`;
    torso.style.transform=`rotate(${s.body.p.toFixed(3)}deg) skewX(${(-s.yaw.p*.04).toFixed(3)}deg)`;
    head.style.transform=`perspective(950px) rotateY(${s.yaw.p.toFixed(3)}deg) rotateX(${s.pitch.p.toFixed(3)}deg) rotateZ(${s.roll.p.toFixed(3)}deg)`;
    armL.style.transform=`rotate(${s.left.p.toFixed(3)}deg)`;armR.style.transform=`rotate(${s.right.p.toFixed(3)}deg)`;
    rig.style.setProperty('--warm-light',s.warm.p.toFixed(4));head.style.setProperty('--blink',clamp(s.blink.p,0,1).toFixed(4));
    robot.style.setProperty('--look-x',`${s.eyeX.p.toFixed(3)}px`);robot.style.setProperty('--look-y',`${s.eyeY.p.toFixed(3)}px`);
    // Bubble side: default keeps the bubble on-screen (robot near the left
    // edge → bubble on its right; otherwise bubble on its left). On the Chart
    // tab the filter controls (TIME RANGE / TYPE) sit on the left, so when the
    // left-side bubble would overlap them (its left edge < filterRight, posted
    // by HomeView) we move the bubble to the robot's right instead.
    // Hysteresis + slow position: the side only flips once the SLOWED
    // position (sideX, a ~0.4 s low-pass of the spring x) has crossed the
    // .32/.44 band. The raw spring value wobbles as it settles near a goal,
    // and that wobble alone made the bubble flip side every frame — the
    // "giật" jitter the user kept seeing near the center of the banner.
    let side=travel.dataset.side==='left'?'left':'right';
    if(side==='left'&&sideX>g.width*.44)side='right';
    else if(side==='right'&&sideX<g.width*.32)side='left';
    if(side==='right'&&speech.classList.contains('visible')&&filterRight!=null){
      // The bubble sits at `right:92%` of the robot container, so its right
      // edge is at container-left + 8%·container-width. The container is
      // 28% of the banner wide (55% on mobile) and centered on the robot,
      // so its left edge = s.x.p − g.w/2. Overlap when the bubble's left
      // edge is left of filterRight. 12px margin = hysteresis: without it,
      // the spring settling near the goal made the bubble edge cross
      // filterRight back and forth, flipping the side every frame.
      const containerLeft=s.x.p-g.w/2;
      const bubbleLeftEdge=containerLeft+g.w*.08-bubbleW*s.z.p;
      if(bubbleLeftEdge<filterRight-12)side='left';
    }
    travel.dataset.side=side;
    const expression=sleeping?'sleepy':action?.kind==='wave'||action?.kind==='write'?'happy':action?.kind==='inspect'?'round':action?.kind==='love'?'heart':null;
    robot.dispatchEvent(new CustomEvent('robot-pose',{detail:{time:clock,depth:s.z.p,yaw:s.yaw.p,bank:s.bank.p,body:s.body.p,roll:s.roll.p,pitch:s.pitch.p,left:s.left.p,right:s.right.p,eyeX:s.eyeX.p,eyeY:s.eyeY.p,blink:clamp(s.blink.p,0,1),warm:s.warm.p,squash:s.squash.p,bob,mood,sleeping,expression,board:action?.kind==='inspect',writing:false,teacher:{active:false}}}));
  }
  function frame(now){
    let remaining=Math.min(.12,lastFrame?(now-lastFrame)/1000:1/60);lastFrame=now;
    if(!reduced&&!document.hidden){
      while(remaining>0){const dt=Math.min(1/120,remaining);simulate(dt);remaining-=dt;}
      render();
    }
    raf=requestAnimationFrame(frame);
  }
  const heart='<svg viewBox="0 0 24 24"><path d="M12 21S2 15 2 8.5C2 2 9 1 12 6c3-5 10-4 10 2.5C22 15 12 21 12 21Z"/></svg>';
  const star='<svg viewBox="0 0 24 24"><path d="m12 0 3.1 8.9L24 12l-8.9 3.1L12 24l-3.1-8.9L0 12l8.9-3.1Z"/></svg>';
  function clearParticles(){particles.getAnimations({subtree:true}).forEach(a=>a.cancel());particles.replaceChildren();}
  function burst(love=true){
    if(reduced)return;
    const b=banner.getBoundingClientRect(),r=robot.getBoundingClientRect(),cx=r.x-b.x+r.width*.55,cy=r.y-b.y+r.height*.35;
    const ring=document.createElement('span');ring.className='neon-ripple';ring.style.left=`${cx}px`;ring.style.top=`${cy}px`;particles.appendChild(ring);
    const ripple=ring.animate([{transform:'translate(-50%,-50%) scale(.2)',opacity:.7},{transform:'translate(-50%,-50%) scale(2.4)',opacity:0}],{duration:1100});
    ripple.onfinish=()=>ring.remove();ripple.oncancel=()=>ring.remove();
    for(let i=0;i<9;i++){
      const el=document.createElement('span');el.className='particle';el.innerHTML=love?heart:star;
      el.style.setProperty('--size',`${rand(12,25)}px`);el.style.setProperty('--color',['#ffacc4','#67e8f9','#a58aef'][i%3]);particles.appendChild(el);
      const a=el.animate([{transform:`translate(${cx}px,${cy}px) scale(.3)`,opacity:0},{opacity:1,offset:.15},{transform:`translate(${cx+rand(-r.width*.6,r.width*.6)}px,${cy-rand(25,r.height*.6)}px) scale(1)`,opacity:0}],{duration:rand(1000,1600),easing:'ease-out'});
      a.onfinish=()=>el.remove();a.oncancel=()=>el.remove();
    }
  }
  function wake(){if(sleeping)setSleep(false);}
  function love(){wake();lastInteraction=clock;moodTo('affectionate',5500);energy=clamp(energy+.15,0,1);perform('love',true);banner.classList.add('loved');clearTimeout(loveTimer);loveTimer=setTimeout(()=>banner.classList.remove('loved'),1600);burst();}
  function setSleep(value){
    sleeping=value;banner.classList.toggle('sleeping',value);action=null;
    moodTo(value?'dreamy':'curious',4000);nextAction=clock+1800;
    if(value){
      // Only sleep-related lines may show while the eyes are closed —
      // say() blocks every other phrase for the whole nap.
      clearTimeout(speechTimer);speech.classList.remove('visible');
      clearTimeout(sleepLineTimer);sleepLineTimer=setTimeout(saySleepLine,800);
    }else{
      clearTimeout(sleepLineTimer);
      // The wake-up line still speaks.
      sayI18n('awake',"I'm awake! Let's play some more ♡");
    }
    render();
  }
  function rememberPointer(e){
    if(e.pointerType==='touch')return;
    rect=banner.getBoundingClientRect();pointer={x:e.clientX,y:e.clientY,nx:clamp((e.clientX-rect.left)/geometry.width,.1,.9),ny:clamp((e.clientY-rect.top)/geometry.height,.1,.9)};pointerLast=clock;
  }
  document.addEventListener('pointermove',rememberPointer,{passive:true});
  banner.addEventListener('pointermove',e=>{
    if(reduced||sleeping)return;
    rect=banner.getBoundingClientRect();banner.style.setProperty('--pointer-x',`${e.clientX-rect.left}px`);banner.style.setProperty('--pointer-y',`${e.clientY-rect.top}px`);
    if(drag){
      const dx=e.clientX-drag.px,dy=e.clientY-drag.py;drag.moved ||= Math.hypot(dx,dy)>7;
      drag.targetX=clamp(drag.x+dx,12,geometry.width-12);drag.targetY=clamp(drag.y+dy,12,geometry.height-12);return;
    }
    if(Math.abs(e.movementX)>24&&clock-lastSurprise>3500&&robot.matches(':hover')){lastSurprise=clock;moodTo('surprised',3200);sayI18n('surprised','Whoa! You surprised me! 😳');}
    if(clock-lastTrail>60&&e.pointerType!=='touch'){
      lastTrail=clock;const s=document.createElement('i');s.className='spark';s.style.left=`${e.clientX-rect.left}px`;s.style.top=`${e.clientY-rect.top}px`;particles.appendChild(s);
      const a=s.animate([{opacity:.7,transform:'scale(1)'},{opacity:0,transform:'translateY(-14px) scale(0)'}],{duration:550});a.onfinish=()=>s.remove();a.oncancel=()=>s.remove();
    }
  });
  robot.addEventListener('pointerenter',()=>{if(!sleeping&&active()&&!drag)sayI18n('hello','Oh, hello there! ✨',1800);});
  robot.addEventListener('pointerdown',e=>{
    if(e.button!==0)return;wake();suppressClick=false;lastInteraction=clock;
    if(reduced)return;
    flightPlan=null;roamUntil=0;moodTo('excited',3500);action=null;
    drag={px:e.clientX,py:e.clientY,x:state.x.p,y:state.y.p,targetX:state.x.p,targetY:state.y.p,moved:false};
    robot.setPointerCapture(e.pointerId);robot.classList.add('dragging');
  });
  function release(e,cancelled=false){
    if(!drag)return;const moved=drag.moved;drag=null;suppressClick=cancelled||moved;robot.classList.remove('dragging');
    if(robot.hasPointerCapture(e.pointerId))robot.releasePointerCapture(e.pointerId);
    goal={x:state.x.p+clamp(state.x.v*.18,-80,80),y:state.y.p+clamp(state.y.v*.18,-50,50),z:state.z.p,at:clock,phase:0};nextGoal=clock+700;nextAction=clock+5500;
    if(moved&&!cancelled){moodTo('playful',4500);sayI18n('dragged','Wheee! Again, please! ✨');burst(false);}
  }
  robot.addEventListener('pointerup',e=>release(e));robot.addEventListener('pointercancel',e=>release(e,true));robot.addEventListener('lostpointercapture',e=>release(e,true));
  robot.addEventListener('click',e=>{if(!suppressClick||e.detail===0)love();suppressClick=false;});
  function syncMotion(){
    reduced=media.matches&&!document.body.classList.contains('motion-forced');
    banner.classList.toggle('reduced',reduced);
    clearParticles();lastFrame=0;render();
  }
  const field=$('.neon-field');if(field)for(let i=0;i<18;i++){
    const el=document.createElement('i');el.style.setProperty('--x',`${(i*37+9)%100}%`);el.style.setProperty('--y',`${(i*23+15)%90}%`);el.style.setProperty('--delay',`${-i*.83}s`);el.style.setProperty('--duration',`${5+i%5}s`);el.style.setProperty('--neon',['#67e8f9','#a58aef','#ffacc4'][i%3]);field.appendChild(el);
  }
  moodTo('curious');measure();new ResizeObserver(measure).observe(banner);
  window.addEventListener('scroll',()=>{rect=banner.getBoundingClientRect();},{passive:true});
  media.addEventListener('change',syncMotion);syncMotion();
  raf=requestAnimationFrame(frame);
  document.addEventListener('visibilitychange',()=>{lastFrame=0;if(document.hidden){cancelAnimationFrame(raf);clearTimeout(speechTimer);clearTimeout(sleepLineTimer);}else{rect=banner.getBoundingClientRect();raf=requestAnimationFrame(frame);if(sleeping){clearTimeout(sleepLineTimer);sleepLineTimer=setTimeout(saySleepLine,1500);}}});
})();
