// Reexport the native module. On web, it will be resolved to ExpoPrecisionMetronomeModule.web.ts
// and on native platforms to ExpoPrecisionMetronomeModule.ts
export { default } from './ExpoPrecisionMetronomeModule';
export { default as ExpoPrecisionMetronomeView } from './ExpoPrecisionMetronomeView';
export * from  './ExpoPrecisionMetronome.types';
