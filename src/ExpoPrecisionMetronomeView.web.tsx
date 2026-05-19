import * as React from 'react';

import { ExpoPrecisionMetronomeViewProps } from './ExpoPrecisionMetronome.types';

export default function ExpoPrecisionMetronomeView(props: ExpoPrecisionMetronomeViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
