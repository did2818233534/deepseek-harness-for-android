// Worker reads stdin and writes stdout/stderr. No path, process, device or network syscalls after lockdown.
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <stddef.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#if defined(__aarch64__)
#define ARCH AUDIT_ARCH_AARCH64
#elif defined(__x86_64__)
#define ARCH AUDIT_ARCH_X86_64
#else
#error unsupported architecture
#endif
#define LOAD(off) BPF_STMT(BPF_LD|BPF_W|BPF_ABS,off)
#define RET(x) BPF_STMT(BPF_RET|BPF_K,x)
#define DENY (SECCOMP_RET_ERRNO|EPERM)
#define OK(n) BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_##n,0,1),RET(SECCOMP_RET_ALLOW)
#define STDIO(n) BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_##n,0,4),LOAD(offsetof(struct seccomp_data,args[0])),BPF_JUMP(BPF_JMP|BPF_JGT|BPF_K,2,0,1),RET(DENY),RET(SECCOMP_RET_ALLOW)
int dsh_lock(void){
 struct rlimit cpu={30,30}, file={20*1024*1024,20*1024*1024}, core={0,0}, mem={768UL<<20,768UL<<20};
 FILE *stat=fopen("/proc/self/statm","r");unsigned long pages; if(!stat)return -1;int got=fscanf(stat,"%lu",&pages);fclose(stat);if(got!=1)return -1;mem.rlim_cur=mem.rlim_max=pages*sysconf(_SC_PAGESIZE)+(384UL<<20);
 if(setrlimit(RLIMIT_CPU,&cpu)||setrlimit(RLIMIT_FSIZE,&file)||setrlimit(RLIMIT_CORE,&core)||setrlimit(RLIMIT_AS,&mem))return -1;
 struct sock_filter filter[]={
 LOAD(offsetof(struct seccomp_data,arch)),BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,ARCH,1,0),RET(SECCOMP_RET_KILL_PROCESS),
 LOAD(offsetof(struct seccomp_data,nr)), STDIO(read),STDIO(write),STDIO(writev),
 BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_mmap,0,4),LOAD(offsetof(struct seccomp_data,args[3])),BPF_JUMP(BPF_JMP|BPF_JSET|BPF_K,MAP_ANONYMOUS,1,0),RET(DENY),RET(SECCOMP_RET_ALLOW),
 OK(brk),OK(munmap),OK(mremap),OK(mprotect),OK(madvise),OK(futex),OK(clock_gettime),OK(gettimeofday),OK(getrandom),OK(getpid),OK(gettid),OK(rt_sigreturn),OK(rt_sigaction),OK(rt_sigprocmask),OK(sigaltstack),OK(close),OK(exit),OK(exit_group),
 RET(DENY)};
 struct sock_fprog prog={sizeof(filter)/sizeof(filter[0]),filter};
 if(prctl(PR_SET_NO_NEW_PRIVS,1,0,0,0))return -1;
 return syscall(__NR_seccomp,SECCOMP_SET_MODE_FILTER,SECCOMP_FILTER_FLAG_TSYNC,&prog)==0?0:-1;
}
__attribute__((constructor)) static void early(void){if(getenv("DSH_LOCK_AT_LOAD") && dsh_lock())_exit(125);}
