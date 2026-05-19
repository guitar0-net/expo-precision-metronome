import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoPrecisionMetronomeViewProps } from './ExpoPrecisionMetronome.types';

const NativeView: React.ComponentType<ExpoPrecisionMetronomeViewProps> =
  requireNativeView('ExpoPrecisionMetronome');

export default function ExpoPrecisionMetronomeView(props: ExpoPrecisionMetronomeViewProps) {
  return <NativeView {...props} />;
}
