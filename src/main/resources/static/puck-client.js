// puck-client.js - styled client renderer for puck-builder (separate from app.js for index.html)
// Fixed per https://github.com/puckeditor/puck: style editing now grouped into `style` + `advanced` objects.
// Keeps JSON view canonical; supports both flat legacy keys and new `c.style` object.

function parseSxJsonStyled(str){
  if(!str) return {};
  try{ const o=JSON.parse(str); return o && typeof o==="object"?o:{} }catch(e){ return {}; }
}
function toKebabStyled(s){ return s.replace(/[A-Z]/g,m=>"-"+m.toLowerCase()); }
function getStyleSourceStyled(c){
  // new: c.style object (from Puck `style` object field) + legacy flat keys
  if (c.style && typeof c.style==="object") {
    const s = {...c.style};
    // legacy flat overrides (if present)
    ["height","width","background","textColor","fontSize","fontWeight","fontFamily","padding","margin","border","borderRadius","textAlign"].forEach(k=>{
      if (c[k]!=null && c[k]!=="") s[k]=c[k];
    });
    if (c.color && !s.textColor) s.textColor=c.color;
    if (c.align && !s.textAlign) s.textAlign=c.align;
    return s;
  }
  return c;
}
function buildInlineStyleStyled(c){
  const s = getStyleSourceStyled(c);
  const advJson = (c.advanced && c.advanced.customCssJson) ? c.advanced.customCssJson : c.sxJson;
  const map = {
    height: s.height, width: s.width, background: s.background, backgroundColor: s.background,
    color: s.textColor || s.color || c.textColor || c.color, fontSize: s.fontSize, fontWeight: s.fontWeight, fontFamily: s.fontFamily,
    padding: s.padding, margin: s.margin, border: s.border, borderRadius: s.borderRadius, textAlign: s.textAlign || s.align || c.textAlign || c.align
  };
  Object.assign(map, parseSxJsonStyled(advJson));
  const parts=[];
  for(const [k,v] of Object.entries(map)){
    if(v==null || v==="") continue;
    if(k==="color" && (v==="primary"||v==="success"||v==="inherit")) continue;
    parts.push(`${toKebabStyled(k)}:${v}`);
  }
  return parts.length? ` style="${parts.join("; ")}"` : "";
}
function buildComponentClassStyled(c, base){
  const extra = (c.advanced && c.advanced.className) ? c.advanced.className : (c.className || c.class || "");
  const cx = (...parts) => parts.filter(Boolean).join(" ");
  return cx(base, extra);
}
function buildLayoutStyleStyled(layout){
  if(!layout) return "";
  // layout can have style object from root
  const s = getStyleSourceStyled(layout);
  const dummy = {
    height:s.height, width:s.width, background:s.background, color:s.textColor,
    fontSize:s.fontSize, fontFamily:s.fontFamily, padding:s.padding, margin:s.margin,
    border:s.border, borderRadius:s.borderRadius,
    sxJson: (layout.advanced && layout.advanced.customCssJson) ? layout.advanced.customCssJson : layout.sxJson,
    style: s, advanced: layout.advanced
  };
  return buildInlineStyleStyled(dummy);
}
// Re-export for puck-builder inline script if needed: window.PuckClient = { buildInlineStyleStyled, ... }
if (typeof window !== "undefined") {
  window.PuckClient = { parseSxJsonStyled, buildInlineStyleStyled, buildComponentClassStyled, buildLayoutStyleStyled };
}
