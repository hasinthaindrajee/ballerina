import ballerina.io;

struct Employee {
    string id;
    string name;
    float salary;
}

io:DelimitedRecordChannel delimitedRecordChannel;

function initFileChannel(string filePath,string permission,string encoding,string rs,string fs){
    io:ByteChannel channel = io:openFile(filePath, permission);
    io:CharacterChannel characterChannel;
    var characterChannelResult = io:createCharacterChannel(channel, encoding);
    match characterChannelResult{
        io:CharacterChannel charChannel => {
           characterChannel = charChannel;
        }
        io:IOError err => {
           throw err;
        }
    }
    var delimitedRecordChannelResult = io:createDelimitedRecordChannel(characterChannel, rs, fs);
    match delimitedRecordChannelResult{
        io:DelimitedRecordChannel recordChannel => {
           delimitedRecordChannel = recordChannel;
        }
        io:IOError err => {
           throw err;
        }
    }
}

function nextRecord () returns (string[]) {
    var result  = delimitedRecordChannel.nextTextRecord();
    string [] empty = [];
    match result{
       string[] fields => {
          return fields;
       }
       io:IOError err => {
          throw err;
       }
    }
    return empty;
}

function writeRecord (string[] fields) {
   var result = delimitedRecordChannel.writeTextRecord(fields);
}

function close(){
    var err = delimitedRecordChannel.closeDelimitedRecordChannel();
}

function hasNextRecord () returns (boolean) {
    return delimitedRecordChannel.hasNextTextRecord();
}

function loadToTable (string filePath) returns (float) {
    float total;
    var result = io:loadToTable(filePath, "\n", ",", "UTF-8", false, typeof Employee);
    match result{
      table <Employee> tb => {
        foreach x in tb {
          total = total + x.salary;
        }
        return total;
      }
      io:IOError err => {
        throw err;
      }
    }
    return -1;
}
