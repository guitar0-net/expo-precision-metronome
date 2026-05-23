import { requireNativeView } from "expo";
import * as React from "react";
import type { StyleProp, ViewStyle } from "react-native";

export type ExpoPrecisionMetronomeViewProps = {
  url: string;
  onLoad: (event: { nativeEvent: { url: string } }) => void;
  style?: StyleProp<ViewStyle>;
};

const NativeView: React.ComponentType<ExpoPrecisionMetronomeViewProps> =
  requireNativeView("ExpoPrecisionMetronome");

export default function ExpoPrecisionMetronomeView(
  props: ExpoPrecisionMetronomeViewProps,
) {
  return <NativeView {...props} />;
}
