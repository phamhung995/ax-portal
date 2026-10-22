<script setup>
// Ambient background — nền không gian nhiều lớp (port từ demo app.css/AppShell.vue):
// base gradient 5 radial glow + 3 orb trôi + plexus constellation 3 lớp + bokeh
// + dot grid 2 tầng + noise + vignette. Decorative: aria-hidden, pointer-events none.
</script>
<template>
<div class="ambient" aria-hidden="true">
  <div class="ambient-orb ambient-orb--cyan"></div>
  <div class="ambient-orb ambient-orb--violet"></div>
  <div class="ambient-orb ambient-orb--teal"></div>
  <svg class="ambient-plexus" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice">
    <defs>
      <filter id="plexus-glow" x="-200%" y="-200%" width="500%" height="500%">
        <feGaussianBlur stdDeviation="3" result="b"/>
        <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
      </filter>
    </defs>
    <!-- Bokeh mờ (góc dưới-trái) -->
    <g class="bokeh">
      <circle class="bokeh-c" cx="120" cy="700" r="30"/>
      <circle class="bokeh-v" cx="300" cy="800" r="22"/>
      <circle class="bokeh-c" cx="80" cy="860" r="26"/>
      <circle class="bokeh-v" cx="440" cy="720" r="16"/>
      <circle class="bokeh-c" cx="220" cy="580" r="18"/>
      <circle class="bokeh-v" cx="560" cy="840" r="24"/>
      <circle class="bokeh-c" cx="640" cy="640" r="14"/>
      <circle class="bokeh-c" cx="150" cy="480" r="12"/>
    </g>
    <!-- ═══ LỚP XA (góc trên-trái): mờ mạnh + tối ═══ -->
    <g class="layer-far">
      <line class="edge dim" x1="240" y1="180" x2="420" y2="260"/>
      <line class="edge dim" x1="420" y1="260" x2="560" y2="160"/>
      <line class="edge dim" x1="240" y1="180" x2="360" y2="420"/>
      <line class="edge dim" x1="360" y1="420" x2="640" y2="380"/>
      <line class="edge dim" x1="420" y1="260" x2="360" y2="420"/>
      <line class="edge dim" x1="560" y1="160" x2="640" y2="380"/>
      <line class="edge dim" x1="640" y1="380" x2="740" y2="520" opacity=".1"/>
      <line class="edge dim" x1="150" y1="300" x2="240" y2="180"/>
      <line class="edge dim" x1="150" y1="300" x2="360" y2="420"/>
      <line class="edge dim" x1="500" y1="340" x2="420" y2="260"/>
      <line class="edge dim" x1="500" y1="340" x2="640" y2="380"/>
      <line class="edge dim" x1="700" y1="240" x2="560" y2="160"/>
      <line class="edge dim" x1="700" y1="240" x2="640" y2="380"/>
      <line class="edge dim" x1="280" y1="520" x2="360" y2="420"/>
      <line class="edge dim" x1="280" y1="520" x2="150" y2="300"/>
      <circle class="node" cx="240" cy="180" r="2" opacity=".45"/>
      <circle class="node v" cx="420" cy="260" r="2" opacity=".5"/>
      <circle class="node" cx="560" cy="160" r="2" opacity=".45"/>
      <circle class="node" cx="360" cy="420" r="2.5" opacity=".5"/>
      <circle class="node v" cx="640" cy="380" r="2.5" opacity=".55"/>
      <circle class="node" cx="150" cy="300" r="2" opacity=".4"/>
      <circle class="node v" cx="500" cy="340" r="2" opacity=".45"/>
      <circle class="node" cx="700" cy="240" r="2" opacity=".4"/>
      <circle class="node v" cx="280" cy="520" r="2" opacity=".45"/>
    </g>
    <!-- ═══ LỚP GIỮA (vùng chuyển tiếp): mờ nhẹ ═══ -->
    <g class="layer-mid">
      <line class="edge dim" x1="740" y1="520" x2="950" y2="560"/>
      <line class="edge dim" x1="740" y1="520" x2="820" y2="640"/>
      <line class="edge dim" x1="820" y1="640" x2="950" y2="560"/>
      <line class="edge dim" x1="820" y1="640" x2="1050" y2="720"/>
      <line class="edge dim" x1="950" y1="560" x2="1050" y2="420"/>
      <line class="edge dim" x1="1050" y1="420" x2="1100" y2="520"/>
      <line class="edge dim" x1="950" y1="560" x2="1100" y2="520"/>
      <line class="edge dim" x1="860" y1="440" x2="950" y2="560"/>
      <line class="edge dim" x1="860" y1="440" x2="740" y2="520"/>
      <line class="edge dim" x1="860" y1="440" x2="1050" y2="420"/>
      <line class="edge dim" x1="1150" y1="600" x2="1100" y2="520"/>
      <line class="edge dim" x1="1150" y1="600" x2="1050" y2="720"/>
      <line class="edge dim" x1="1150" y1="600" x2="950" y2="560"/>
      <line class="edge dim" x1="1200" y1="380" x2="1050" y2="420"/>
      <line class="edge dim" x1="1200" y1="380" x2="1100" y2="520"/>
      <circle class="node" cx="740" cy="520" r="2.5" opacity=".55"/>
      <circle class="node v" cx="820" cy="640" r="2.5" opacity=".5"/>
      <circle class="node" cx="950" cy="560" r="3"/>
      <circle class="node v" cx="1050" cy="420" r="2.5"/>
      <circle class="node" cx="860" cy="440" r="2.5" opacity=".5"/>
      <circle class="node v" cx="1150" cy="600" r="2.5" opacity=".55"/>
      <circle class="node" cx="1200" cy="380" r="2.5" opacity=".5"/>
    </g>
    <!-- ═══ LỚP GẦN (cụm dưới-phải): nét, sáng nhất ═══ -->
    <g>
      <g fill="none">
        <line class="edge" x1="1050" y1="720" x2="1180" y2="640"/>
        <line class="edge" x1="1180" y1="640" x2="1100" y2="520"/>
        <line class="edge v" x1="1100" y1="520" x2="1240" y2="520"/>
        <line class="edge" x1="1240" y1="520" x2="1180" y2="640"/>
        <line class="edge" x1="1240" y1="520" x2="1320" y2="420"/>
        <line class="edge v" x1="1320" y1="420" x2="1450" y2="420"/>
        <line class="edge" x1="1450" y1="420" x2="1560" y2="520"/>
        <line class="edge v" x1="1560" y1="520" x2="1420" y2="560"/>
        <line class="edge" x1="1420" y1="560" x2="1320" y2="420"/>
        <line class="edge" x1="1420" y1="560" x2="1240" y2="520"/>
        <line class="edge v" x1="1420" y1="560" x2="1360" y2="700"/>
        <line class="edge" x1="1360" y1="700" x2="1520" y2="680"/>
        <line class="edge" x1="1520" y1="680" x2="1560" y2="520"/>
        <line class="edge v" x1="1360" y1="700" x2="1280" y2="780"/>
        <line class="edge" x1="1280" y1="780" x2="1180" y2="640"/>
        <line class="edge" x1="1280" y1="780" x2="1480" y2="820"/>
        <line class="edge v" x1="1480" y1="820" x2="1520" y2="680"/>
        <line class="edge" x1="1480" y1="820" x2="1580" y2="860"/>
        <line class="edge v" x1="1580" y1="860" x2="1400" y2="880"/>
        <line class="edge" x1="1400" y1="880" x2="1280" y2="780"/>
        <line class="edge" x1="1400" y1="880" x2="1200" y2="860"/>
        <line class="edge v" x1="1200" y1="860" x2="1280" y2="780"/>
        <line class="edge" x1="1200" y1="860" x2="950" y2="820"/>
        <line class="edge v" x1="950" y1="820" x2="1050" y2="720"/>
        <line class="edge" x1="1050" y1="720" x2="1200" y2="860"/>
        <line class="edge" x1="1180" y1="640" x2="1360" y2="700" opacity=".3"/>
        <line class="edge" x1="1560" y1="520" x2="1580" y2="380"/>
        <line class="edge v" x1="1580" y1="380" x2="1450" y2="420"/>
        <line class="edge" x1="1320" y1="420" x2="1400" y2="300"/>
        <line class="edge v" x1="1400" y1="300" x2="1580" y2="380"/>
        <line class="edge" x1="1240" y1="520" x2="1200" y2="420"/>
        <line class="edge v" x1="1200" y1="420" x2="1320" y2="420"/>
        <line class="edge" x1="1200" y1="420" x2="1100" y2="520"/>
        <line class="edge" x1="1200" y1="420" x2="1400" y2="300"/>
        <line class="edge v" x1="1360" y1="600" x2="1420" y2="560"/>
        <line class="edge" x1="1360" y1="600" x2="1320" y2="420"/>
        <line class="edge v" x1="1360" y1="600" x2="1360" y2="700"/>
        <line class="edge" x1="1360" y1="600" x2="1240" y2="520"/>
        <line class="edge" x1="1460" y1="740" x2="1360" y2="700"/>
        <line class="edge v" x1="1460" y1="740" x2="1520" y2="680"/>
        <line class="edge" x1="1460" y1="740" x2="1480" y2="820"/>
        <line class="edge v" x1="1140" y1="760" x2="1050" y2="720"/>
        <line class="edge" x1="1140" y1="760" x2="1280" y2="780"/>
        <line class="edge v" x1="1140" y1="760" x2="1200" y2="860"/>
        <line class="edge" x1="1500" y1="260" x2="1400" y2="300"/>
        <line class="edge v" x1="1500" y1="260" x2="1580" y2="380"/>
        <line class="edge" x1="1500" y1="260" x2="1450" y2="420"/>
      </g>
      <g filter="url(#plexus-glow)">
        <circle class="node" cx="1050" cy="720" r="3"/>
        <circle class="node v hot" cx="1180" cy="640" r="4"/>
        <circle class="node" cx="1100" cy="520" r="3"/>
        <circle class="node hot" cx="1240" cy="520" r="4"/>
        <circle class="node v" cx="1320" cy="420" r="3.5"/>
        <circle class="node hot" cx="1450" cy="420" r="4.5"/>
        <circle class="node v" cx="1560" cy="520" r="3"/>
        <circle class="node hot" cx="1420" cy="560" r="4"/>
        <circle class="node v hot" cx="1360" cy="700" r="4"/>
        <circle class="node" cx="1520" cy="680" r="3.5"/>
        <circle class="node" cx="1280" cy="780" r="3.5"/>
        <circle class="node v hot" cx="1480" cy="820" r="4"/>
        <circle class="node" cx="1580" cy="860" r="3"/>
        <circle class="node v" cx="1400" cy="880" r="3"/>
        <circle class="node" cx="1200" cy="860" r="3"/>
        <circle class="node v" cx="950" cy="820" r="3"/>
        <circle class="node" cx="1580" cy="380" r="2.5"/>
        <circle class="node v" cx="1400" cy="300" r="2.5"/>
        <circle class="node" cx="1200" cy="420" r="3"/>
        <circle class="node v" cx="1360" cy="600" r="3.5"/>
        <circle class="node hot" cx="1460" cy="740" r="3.5"/>
        <circle class="node v" cx="1140" cy="760" r="3"/>
        <circle class="node" cx="1500" cy="260" r="2.5"/>
      </g>
    </g>
  </svg>
  <div class="ambient-grid"></div>
  <span class="ambient-node" style="width:5px; height:5px; top:14%; left:22%"></span>
  <span class="ambient-node" style="width:4px; height:4px; top:32%; left:78%"></span>
  <span class="ambient-node" style="width:6px; height:6px; top:55%; left:12%"></span>
  <span class="ambient-node" style="width:4px; height:4px; top:68%; left:64%"></span>
  <span class="ambient-node" style="width:5px; height:5px; top:24%; left:48%"></span>
  <span class="ambient-node ambient-node--violet" style="width:4px; height:4px; top:44%; left:36%"></span>
  <span class="ambient-node" style="width:5px; height:5px; top:82%; left:30%"></span>
  <span class="ambient-node ambient-node--violet" style="width:4px; height:4px; top:88%; left:72%"></span>
  <span class="ambient-node" style="width:4px; height:4px; top:8%; left:60%"></span>
  <span class="ambient-node" style="width:5px; height:5px; top:62%; left:88%"></span>
  <div class="ambient-noise"></div>
  <div class="ambient-vignette"></div>
</div>
</template>
