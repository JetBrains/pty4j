const tty = require('tty');

function printSize(stream) {
  stream.write(`columns: ${stream.columns}, rows: ${stream.rows}\n`);
}

function listenResize(stream, streamType) {
  if (!tty.isatty(stream.fd)) {
    console.error("Not a tty");
    process.exit(1);
  }
  printSize(stream);
  stream.on('resize', () => {
    printSize(stream);
  });
}

listenResize(process.stdout);

const readline = require('readline');
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', function(line){
  if (line == 'exit') {
    process.exit(0);
  }
});
