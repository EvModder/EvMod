interface FileErrorListProps {
  errors: string[];
}

const splitFileMessage = (text: string) => {
  const sep = text.indexOf(": ");
  return sep > 0
    ? { file: text.slice(0, sep), message: text.slice(sep + 2) }
    : { file: undefined, message: text };
};

export default function FileErrorList({ errors }: FileErrorListProps) {
  if (!errors.length) return null;
  return (
    <ul className="mt-4 list-disc space-y-1 pl-5 text-sm">
      {errors.map((text, i) => {
        const { file, message } = splitFileMessage(text);
        return (
          <li className="text-red-500" key={`${i}-${text}`}>
            {file && <span className="font-semibold text-foreground">{file}: </span>}
            {message}
          </li>
        );
      })}
    </ul>
  );
}
