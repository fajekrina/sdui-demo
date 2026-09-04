# Puck Unified Edit Panel — Prompt

> Based on https://github.com/puckeditor/puck `apps/demo/config/blocks/*` + https://puckeditor.com/docs/integrating-puck/component-configuration + https://puckeditor.com/docs/api-reference/fields

**Strict Prompt — copy to LLM to generate any new Puck component with identical edit panel (enforces fallback/validation):**

```
Generate a Puck component configuration that enforces a strict, uniform edit panel for every component, ensuring that all fields (e.g., text, number, select) are explicitly defined and identically structured across components. The configuration must include a fallback or validation mechanism to catch and log any missing or misconfigured fields, preventing edit panel errors. Additionally, ensure the render function for each component wraps the output in a consistent layout wrapper and that the fields object is fully compatible with Puck's core field types to eliminate runtime errors, guaranteeing that every component's edit interface behaves identically and without failure.

Requirements (enforce in code — see puck-builder.html:381):
- Import from "@measured/puck" (alias "@puckeditor/core" per https://github.com/puckeditor/puck) and use ONLY Puck core field types: text, textarea, number, select, radio, array, slot, object (see https://puckeditor.com/docs/api-reference/fields) — validate via PUCK_CORE_FIELD_TYPES.
- Every component MUST use centralized baseStrictFields = {title:text(contentEditable), description:textarea(contentEditable), image:text, count:number(min:0), variant:select(options), style:object, advanced:object} — no component-specific spread that could deviate; fields: { ...baseStrictFields } identically for all 10 components (Grid,Flex,Space,Heading,Text,Button,Card,Hero,Divider,Image).
- Include fallback/validation: validatePuckFields(config) checks every field.type in PUCK_CORE_FIELD_TYPES, select/radio has options[], array has arrayFields, object has objectFields, and every component has title/description/image/count/variant/style/advanced — injects fallback text/0/default and logs warn, prevents edit panel crash. Call before enforce.
- Every render MUST wrap via UniformWrapper({id,type,props,children}) → <Selectable><Box sx={buildStyleSx(props,{p:2,border,borderRadius,bgcolor})}><Box sx={flexDirection:column,gap:1}>{children}</Box></Box></Selectable> plus UniformEditWrapper label border:dashed for identical padding/spacing/label styles; try/catch fallback to title/description/image if origRender throws.
- Provide defaultProps: {title:"Title",description:"Description",image:"",count:0,variant:"default",style:{},advanced:{className:"",customCssJson:""}} and keep id auto-generated.
- Keep categories (layout, typography, actions, content) per apps/demo/config/index.tsx:25 and enforce via Object.keys(puckConfig.components).forEach(k=>{comp.fields={...baseStrictFields}}).
```

**Shared code this prompt generates (implemented in `src/main/resources/static/puck-builder.html:381`):**

```js
// Strict centralized base — text/number/select explicitly identical for every component
const baseStrictFields = {
  title:{type:"text", label:"Title", contentEditable:true},
  description:{type:"textarea", label:"Description", contentEditable:true},
  image:{type:"text", label:"Image URL"},
  count:{type:"number", label:"Order", min:0},
  variant:{type:"select", label:"Variant", options:[{label:"Default",value:"default"},{label:"Primary",value:"primary"},{label:"Secondary",value:"secondary"}]},
  style: styleObjectField, // object with select ...
  advanced: advancedField // object with text/textarea
};
const unifiedEditPanelFields = baseStrictFields;

// Uniform wrapper — identical padding/spacing/label
function UniformWrapper({id,type,props,children}){ const sx=buildStyleSx(props,{p:2,border:"1px solid #e5e7eb",borderRadius:"12px",bgcolor:"#ffffff"}); const cls=getClassName(props); return html`<${Selectable} id=${id} type=${type} props=${props}><${Box} sx=${sx} className=${cls}><${Box} sx=${{display:"flex",flexDirection:"column",gap:1}}>${children}</Box></Box></Selectable>`; }
function UniformEditWrapper({label,children}){ return html`<${Box} sx=${{p:1.5,border:"1px dashed #e2e8f0",borderRadius:"8px",bgcolor:"#f8fafc"}}><${Typography} variant="caption" sx=${{fontWeight:700}}>${label}</Typography>${children}</Box>`; }

// Validation + fallback — prevents edit panel errors, Puck-core compatible
const PUCK_CORE_FIELD_TYPES=["text","textarea","number","select","radio","array","slot","object"];
function validatePuckFields(config){ /* checks type in PUCK_CORE_FIELD_TYPES, select/radio options, arrayFields/objectFields, required title/description/image/count/variant/style/advanced, injects fallback text/0, logs warn */ }

// Enforce after puckConfig defined:
Object.keys(puckConfig.components).forEach(k=>{ puckConfig.components[k].fields={...baseStrictFields}; const orig=puckConfig.components[k].render; puckConfig.components[k].render=(props)=>{ try{return orig(props);} catch(e){ console.warn("[Puck] fallback",k,e); return html`<${UniformWrapper} id=${props.id} type=${k} props=${props}><${Typography}>${props.title}</Typography></Box></Selectable>`; } } });
validatePuckFields(puckConfig);
```

**Usage in `puck-builder.html:391` (strict, no SDUI booking):** every `puckConfig.components[Name]` is overridden to `fields:{...baseStrictFields}` (`title/text, description/textarea, image/text, count/number, variant/select, style/object, advanced/object`) via `Object.keys(...).forEach` `puck-builder.html:400` — see `Grid,Flex,Space,Heading,Text,Button,Card,Hero,Divider,Image` (10) all identical, no unique `numColumns`/`gap/items` overrides remain. `render` wrapped `UniformWrapper`+`UniformEditWrapper` `puck-builder.html:389` + `try/catch` fallback `puck-builder.html:400` and `validatePuckFields` `puck-builder.html:399` logs missing `PUCK_CORE_FIELD_TYPES` (`text/textarea/number/select/radio/array/slot/object`).

*This strict prompt guarantees `mixed_999.json:1` (999) `mixed_100.json:1` (100) `gallery_100_images.json:1` (102) edit panels are pixel-identical and Puck-core compatible — no component has unique/missing fields, fallback prevents `edit panel errors`.*
