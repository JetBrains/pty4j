function printTerminalSize(source) {
  process.stdout.write(`${source}: columns: ${process.stdout.columns}, rows: ${process.stdout.rows}\n`);
}

if (!process.stdout.isTTY) {
  console.error("Not a tty");
  process.exit(1);
}

process.stdout.on('resize', () => {
  printTerminalSize('resize');
});

printTerminalSize('init');

process.stdin.setRawMode(true);

process.stdin.on('data', (data) => {
  if (data.toString() === 'q') {
    process.exit(0);
  }
});
