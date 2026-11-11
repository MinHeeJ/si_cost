import { Text, type TextProps } from "./Text";

export function Decimal(props: TextProps) {
  return (
    <Text
      {...props}
      type="text"
      inputMode="decimal"
      className="sicost-decimal"
    />
  );
}
