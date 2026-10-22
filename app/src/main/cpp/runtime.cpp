#include <jni.h>
#include <node.h>
#include <vector>
#include <string>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
extern "C" JNIEXPORT jint JNICALL Java_dev_guga_dsh_HarnessService_startNode(JNIEnv* env,jclass,jobjectArray arguments,jstring logPath){
 const char* log=env->GetStringUTFChars(logPath,nullptr);int fd=open(log,O_CREAT|O_WRONLY|O_APPEND,0600);env->ReleaseStringUTFChars(logPath,log);if(fd>=0){dup2(fd,STDOUT_FILENO);dup2(fd,STDERR_FILENO);close(fd);}
 int argc=env->GetArrayLength(arguments);std::vector<std::string> values;size_t size=0;
 for(int i=0;i<argc;i++){auto s=(jstring)env->GetObjectArrayElement(arguments,i);const char* p=env->GetStringUTFChars(s,nullptr);values.emplace_back(p);size+=values.back().size()+1;env->ReleaseStringUTFChars(s,p);env->DeleteLocalRef(s);}
 std::vector<char> storage(size);std::vector<char*> argv(argc);char* cursor=storage.data();for(int i=0;i<argc;i++){argv[i]=cursor;memcpy(cursor,values[i].c_str(),values[i].size()+1);cursor+=values[i].size()+1;}
 return node::Start(argc,argv.data());
}
