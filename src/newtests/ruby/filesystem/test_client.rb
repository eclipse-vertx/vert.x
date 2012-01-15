require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

FILEDIR = "ruby-test-output"

def setup
  FileSystem::exists?(FILEDIR).handler do |exists|
    if exists
      FileSystem::delete_recursive(FILEDIR).handler do
        FileSystem::mkdir(FILEDIR).handler do
          @tu.app_ready
        end
      end
    else
      FileSystem::mkdir(FILEDIR).handler do
        @tu.app_ready
      end
    end
  end
end

def teardown(&blk)
  FileSystem::delete_recursive(FILEDIR).handler do
    FileSystem::mkdir(FILEDIR).handler do
      blk.call
    end
  end
end

def test_stats
  filename = FILEDIR + "/test-file.txt"
  FileSystem::create_file(filename).handler do
    FileSystem::props(filename).handler do |compl|
      @tu.azzert(compl.succeeded?)
      stats = compl.result
#          puts "creation time #{stats.creation_time}"
#          puts "last access time #{stats.last_access_time}"
#          puts "last modification time #{stats.last_modified_time}"
#          puts "directory? #{stats.directory?}"
#          puts "regular file? #{stats.regular_file?}"
#          puts "symbolic link? #{stats.symbolic_link?}"
#          puts "other? #{stats.other?}"
#          puts "size #{stats.size}"
      @tu.azzert(stats.regular_file?)
      @tu.test_complete
    end
  end
end

def test_async_file
  FileSystem::open(FILEDIR + "/somefile.txt").handler do |compl|
    @tu.azzert(compl.succeeded?)
    file = compl.result
    num_chunks = 100;
    chunk_size = 1000;
    tot_buff = Buffer.create(0)
    written =0
    for i in 0..num_chunks - 1
      buff = TestUtils.gen_buffer(chunk_size)
      tot_buff.append_buffer(buff)
      file.write(buff, i * chunk_size).handler do
        written += 1
        if written == num_chunks
          # all written
          tot_read = Buffer.create(0)
          read = 0
          for j in 0..num_chunks - 1
            pos = j * chunk_size
            file.read(tot_read, pos, pos, chunk_size).handler do |compl|
              @tu.azzert(compl.succeeded?)
              buff = compl.result
              read += 1
              if read == num_chunks
                # all read
                @tu.azzert(TestUtils.buffers_equal(tot_buff, tot_read))
                file.close.handler do
                  @tu.test_complete
                end
              end
            end
          end
        end
      end
    end
  end
end

def test_async_file_streams
  filename = FILEDIR + "/somefile.txt"
  FileSystem::open(filename).handler do |compl|
    @tu.azzert(compl.succeeded?)
    file = compl.result
    num_chunks = 100;
    chunk_size = 1000;
    tot_buff = Buffer.create(0)
    write_stream = file.write_stream
    for i in 0..num_chunks - 1
      buff = TestUtils.gen_buffer(chunk_size)
      tot_buff.append_buffer(buff)
      write_stream.write_buffer(buff)
    end
    file.close.handler do
      FileSystem::open(filename).handler do |compl|
        @tu.azzert(compl.succeeded?)
        file = compl.result
        read_stream = file.read_stream
        tot_read = Buffer.create(0)
        read_stream.data_handler do |data|
          tot_read.append_buffer(data)
        end
        read_stream.end_handler do
          @tu.azzert(TestUtils.buffers_equal(tot_buff, tot_read))
          file.close.handler do
            @tu.test_complete
          end
        end
      end
    end
  end
end

def vertx_stop
  teardown do
    @tu.unregister_all
    @tu.app_stopped
  end
end

@tu.register_all(self)
setup
