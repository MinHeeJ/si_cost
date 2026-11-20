import { Text, type TextProps } from "./Text";

export function Decimal(props: TextProps) {
  return (
    <Text
      {...props}
      numericKind={props.numericKind ?? "decimal"}
      type="text"
      inputMode="decimal"
      className="sicost-decimal"
    />
  );
}
